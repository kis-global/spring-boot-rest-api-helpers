package springboot.rest.specifications;

import org.apache.commons.lang3.StringUtils;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.IdentifiableType;
import javax.persistence.metamodel.Metamodel;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class CustomSpecifications<T> {

    @PersistenceContext
    private EntityManager em;

    public Specification<T> customSpecificationBuilder(Map<String, Object> map) {
        return (Specification<T>) (root, query, builder) -> {
            query.distinct(true);
            List<Predicate> predicates = handleMap(builder, root, null, query, map, new ArrayList<>());
            return builder.and(predicates.toArray(new Predicate[predicates.size()]));
        };
    }

    public Predicate customSpecificationBuilder(CriteriaBuilder builder, CriteriaQuery query, Root root, Map<String, Object> map) {
        query.distinct(true);
        List<Predicate> predicates = handleMap(builder, root, null, query, map, new ArrayList<>());
        return builder.and(predicates.toArray(new Predicate[predicates.size()]));
    }

    public Predicate customSpecificationBuilder(CriteriaBuilder builder, CriteriaQuery query, Root root, Map<String, Object> map, List<String> includeOnlyFields) {
        query.distinct(true);
        List<Predicate> predicates = handleMap(builder, root, null, query, map, includeOnlyFields);
        return builder.and(predicates.toArray(new Predicate[predicates.size()]));
    }

    public Predicate customSpecificationBuilder(CriteriaBuilder builder, CriteriaQuery query, Root root, List<Map<String, Object>> list) {
        query.distinct(true);
        List<Predicate> orPredicates = new ArrayList<>();
        for (Map<String, Object> map : list) {
            List<Predicate> predicates = handleMap(builder, root, null, query, map, new ArrayList<>());
            Predicate orPred = builder.and(predicates.toArray(new Predicate[predicates.size()]));
            orPredicates.add(orPred);
        }
        return builder.or(orPredicates.toArray(new Predicate[orPredicates.size()]));
    }

    public List<Predicate> handleMap(CriteriaBuilder builder, Root root, Join join, CriteriaQuery query,
                                     Map<String, Object> map, List<String> includeOnlyFields) {

        if (join != null) {
            root = query.from(getJavaTypeOfClassContainingAttribute(root, join.getAttribute().getName()));
        }
        List<Predicate> predicates = new ArrayList<>();
        Predicate pred;
        if (map.containsKey("q") && map.get("q") instanceof String) {
            predicates.add(searchInAllAttributesPredicate(builder, root, (String) map.get("q"), includeOnlyFields));
            map.remove("q");
        }
        Set<Attribute<? super T, ?>> attributes = root.getModel().getAttributes();
        for (Map.Entry e : map.entrySet()) {
            String key = (String) e.getKey();
            Object val = e.getValue();
            String cleanKey = cleanUpKey(key);

            Attribute a = root.getModel().getAttribute(cleanKey);
            if (attributes.contains(a)) {
                pred = handleAllCases(builder, root, join, query, a, key, val);
                predicates.add(pred);
            }
        }
        return predicates;
    }

    public Predicate handleAllCases(CriteriaBuilder builder, Root root, Join join, CriteriaQuery query,
                                    Attribute a, String key, Object val) {

        boolean isValueCollection = val instanceof Collection;
        boolean isValueMap = val instanceof Map;
        String cleanKey = cleanUpKey(key);
        boolean isKeyClean = cleanKey.equals(key);
        boolean isNegation = key.endsWith("Not");
        boolean isGt = key.endsWith("Gt");
        boolean isGte = key.endsWith("Gte");
        boolean isLt = key.endsWith("Lt");
        boolean isLte = key.endsWith("Lte");
        boolean isConjunction = key.endsWith("And");
        boolean isAssociation = a.isAssociation();

        if (isValueMap) {
            val = convertMapContainingPrimaryIdToValue(val, a, root);
        }
        if (val instanceof Map && isAssociation) {
            List<Predicate> predicates = handleMap(builder, root, addJoinIfNotExists(root, a, isValueCollection, isConjunction), query, ((Map) val), Arrays.asList());
            Predicate[] predicatesArray = predicates.toArray(new Predicate[predicates.size()]);
            return builder.and(predicatesArray);
        }

        if (isKeyClean) {
            return handleCleanKeyCase(builder, root, join, query, cleanKey, a, val);
        } else if (isNegation) {
            return builder.not(handleCleanKeyCase(builder, root, join, query, cleanKey, a, val));
        } else if (isConjunction) {
            if (isValueCollection) {
                return handleCollection(builder, root, join, query, a, cleanKey, (Collection) val, true);
            }
        } else if (isLte) {
            return invokePredicate(builder, root, a, val, "lessThanOrEqualTo");
        } else if (isGte) {
            return invokePredicate(builder, root, a, val, "greaterThanOrEqualTo");
        } else if (isLt) {
            return invokePredicate(builder, root, a, val, "lessThan");
        } else if (isGt) {
            return invokePredicate(builder, root, a, val, "greaterThan");
        }
        return builder.conjunction();
    }

    public Predicate handleCollection(CriteriaBuilder builder, Root root, Join join, CriteriaQuery query,
                                      Attribute a, String key, Collection values, boolean conjunction) {

        List<Predicate> predicates = new ArrayList<>();
        for (Object val : values) {
            Predicate pred = handleAllCases(builder, root, join, query, a, key, val);
            predicates.add(pred);
        }
        Predicate[] predicatesArray = predicates.toArray(new Predicate[predicates.size()]);
        return (conjunction) ? builder.and(predicatesArray) : builder.or(predicatesArray);
    }

    public Predicate handleCleanKeyCase(CriteriaBuilder builder, Root root, Join join, CriteriaQuery query,
                                        String key, Attribute a, Object val) {

        boolean isValueCollection = val instanceof Collection;
        boolean isValTextSearch = (val instanceof String) && ((String) val).contains("%");
        if (isValueCollection) {
            return handleCollection(builder, root, join, query, a, key, (Collection) val, false);
        } else if (isValTextSearch) {
            return createLikePredicate(builder, root, join, a, (String) val);
        } else if (a.isCollection() && !a.isAssociation()) {
            return createEqualityPredicate(builder, root, addJoinIfNotExists(root, a, false, isValueCollection), a, val);
        } else {
            return createEqualityPredicate(builder, root, join, a, val);
        }
    }

    //https://stackoverflow.com/a/16911313/986160
    //https://stackoverflow.com/a/47793003/986160
    public Attribute getIdAttribute(EntityManager em, Class<T> clazz) {
        Metamodel m = em.getMetamodel();
        IdentifiableType<T> of = (IdentifiableType<T>) m.managedType(clazz);
        return of.getId(of.getIdType().getJavaType());
    }

    private String cleanUpKey(String key) {
        List<String> postfixes = Arrays.asList("Gte", "Gt", "Lte", "Lt", "Not", "And");
        for (String postfix : postfixes) {
            if (key.endsWith(postfix)) {
                return key.substring(0, key.length() - postfix.length());
            }
        }
        return key;
    }

    public Predicate searchInAllAttributesPredicate(CriteriaBuilder builder, Root root, String text,
                                                    List<String> includeOnlyFields) {

        if (!text.contains("%")) {
            text = "%" + text + "%";
        }
        final String finalText = text;

        Set<Attribute> attributes = root.getModel().getAttributes();
        List<Predicate> orPredicates = new ArrayList<>();
        for (Attribute a : attributes) {
            boolean javaTypeIsString = a.getJavaType().getSimpleName().equalsIgnoreCase("string");
            boolean shouldSearch = includeOnlyFields.isEmpty() || includeOnlyFields.contains(a.getName());
            if (javaTypeIsString && shouldSearch) {
                Predicate orPred = builder.like(root.get(a.getName()), finalText);
                orPredicates.add(orPred);
            }
        }

        return builder.or(orPredicates.toArray(new Predicate[orPredicates.size()]));
    }

    /**
     * creates an equality predicate.
     * Basically it checks for type and does the right predicate.
     *
     * @param builder CriteriaBuilder
     * @param root    Root
     * @param join    Join Context
     * @param a       Attribute
     * @param val     Object for comparison (from user)
     * @return Predicate
     */
    private Predicate createEqualityPredicate(CriteriaBuilder builder, Root root, Join join, Attribute a, Object val) {
        if (isNull(a, val)) {
            if (a.isAssociation() && a.isCollection()) {
                return builder.isEmpty(root.get(a.getName()));
            } else if (isPrimitive(a)) {
                return builder.isNull(root.get(a.getName()));
            } else {
                return root.get(a.getName()).isNull();
            }
        } else if (join == null) {
            if (isEnum(a)) {
                return builder.equal(root.get(a.getName()), Enum.valueOf(Class.class.cast(a.getJavaType()), (String) val));
            } else if (isPrimitive(a)) {
                return builder.equal(root.get(a.getName()), val);
            } else if (isUUID(a)) {
                return builder.equal(root.get(a.getName()), UUID.fromString(val.toString()));
            } else if (a.isAssociation()) {
                if (isPrimaryKeyOfAttributeUUID(a, root)) {
                    return prepareJoinAssociatedPredicate(builder, root, a, UUID.fromString(val.toString()));
                } else {
                    return prepareJoinAssociatedPredicate(builder, root, a, val);
                }
            }
        } else if (join != null) {
            if (isEnum(a)) {
                return builder.equal(join.get(a.getName()), Enum.valueOf(Class.class.cast(a.getJavaType()), (String) val));
            } else if (isPrimitive(a)) {
                return builder.equal(join.get(a.getName()), val);
            } else if (a.isAssociation()) {
                return builder.equal(join.get(a.getName()), val);
            } else if (a.isCollection()) {
                return builder.equal(join, val);
            }
        }
        throw new IllegalArgumentException("equality/inequality is currently supported on primitives and enums");
    }

    private Predicate createLikePredicate(CriteriaBuilder builder, Root<T> root, Join join, Attribute a, String val) {
        if (join == null) {
            return builder.like(root.get(a.getName()).as(String.class), val);
        } else {
            return builder.like(join.get(a.getName()).as(String.class), val);
        }
    }

    private Predicate invokePredicate(CriteriaBuilder builder, Root root, Attribute a, Object val, String methodName) {
        SimpleDateFormat formatterDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        DateTimeFormatter formatterLD = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter formatterLDT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        try {
            if (LocalDate.class.equals(a.getJavaType())) {
                return invokePredicateMethod(methodName, builder, root, a, LocalDate.parse((String) val, formatterLD));
            } else if (Date.class.equals(a.getJavaType())) {
                return invokePredicateMethod(methodName, builder, root, a, formatterDate.parse(((String) val)));
            } else if (LocalDateTime.class.equals(a.getJavaType())) {
                return invokePredicateMethod(methodName, builder, root, a, LocalDateTime.parse((String) val, formatterLDT));
            } else if (Timestamp.class.equals(a.getJavaType())) {
                return invokePredicateMethod(methodName, builder, root, a, new Timestamp((Long) val));
            } else if (String.class.equals(a.getJavaType())) {
                return invokePredicateMethod(methodName, builder, root, a, (String) val);
            } else if (val instanceof Integer) {
                invokePredicateMethod(methodName, builder, root, a, (Integer) val);
            }
            throw new IllegalArgumentException("val type not supported yet for lower-equals");
        } catch (DateTimeParseException | ParseException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    private <Y extends Comparable<? super Y>> Predicate invokePredicateMethod(String methodName,
                                                                              CriteriaBuilder builder,
                                                                              Root root,
                                                                              Attribute a,
                                                                              Y value) {
        if ("greaterThanOrEqualTo".equals(methodName)) {
            return builder.greaterThanOrEqualTo(root.get(a.getName()), value);
        }
        if ("lessThan".equals(methodName)) {
            return builder.lessThan(root.get(a.getName()), value);
        }
        if ("lessThanOrEqualTo".equals(methodName)) {
            return builder.lessThanOrEqualTo(root.get(a.getName()), value);
        }
        if ("greaterThan".equals(methodName)) {
            return builder.greaterThan(root.get(a.getName()), value);
        }
        throw new IllegalArgumentException();
    }

    private Predicate prepareJoinAssociatedPredicate(CriteriaBuilder builder, Root root, Attribute a, Object val) {
        Path rootJoinGetName = addJoinIfNotExists(root, a, false, false);
        Class referencedClass = rootJoinGetName.getJavaType();
        String referencedPrimaryKey = getIdAttribute(em, referencedClass).getName();
        return builder.equal(rootJoinGetName.get(referencedPrimaryKey), val);
    }

    private Join addJoinIfNotExists(Root root, Attribute a, boolean isConjunction, boolean isValueCollection) {
        if (isConjunction && isValueCollection) {
            return root.join(a.getName());
        }

        Set<Join> joins = root.getJoins();
        Join toReturn = null;
        for (Join join : joins) {
            if (a.getName().equals(join.getAttribute().getName())) {
                toReturn = join;
                break;
            }
        }
        if (toReturn == null) {
            toReturn = root.join(a.getName());
        }
        return toReturn;
    }

    private Class getJavaTypeOfClassContainingAttribute(Root root, String attributeName) {
        Attribute a = root.getModel().getAttribute(attributeName);
        if (a.isAssociation()) {
            return addJoinIfNotExists(root, a, false, false).getJavaType();
        }
        return null;
    }

    private boolean isPrimaryKeyOfAttributeUUID(Attribute a, Root root) {
        Class javaTypeOfAttribute = getJavaTypeOfClassContainingAttribute(root, a.getName());
        String primaryKeyName = getIdAttribute(em, javaTypeOfAttribute).getJavaType().getSimpleName().toLowerCase();
        return primaryKeyName.equalsIgnoreCase("uuid");
    }

    private Object convertMapContainingPrimaryIdToValue(Object val, Attribute a, Root root) {
        Class javaTypeOfAttribute = getJavaTypeOfClassContainingAttribute(root, a.getName());
        String primaryKeyName = getIdAttribute(em, javaTypeOfAttribute).getName();
        if (val instanceof Map && ((Map) val).keySet().size() == 1) {
            Map map = ((Map) val);
            for (Object key : map.keySet()) {
                if (key.equals(primaryKeyName)) {
                    return map.get(primaryKeyName);
                }
            }
        }
        return val;
    }

    private boolean isUUID(Attribute attribute) {
        String attributeJavaClass = attribute.getJavaType().getSimpleName().toLowerCase();
        return attributeJavaClass.equalsIgnoreCase("uuid");
    }

    private boolean isPrimitive(Attribute attribute) {
        String attributeJavaClass = attribute.getJavaType().getSimpleName().toLowerCase();
        return attributeJavaClass.startsWith("int") ||
                attributeJavaClass.startsWith("long") ||
                attributeJavaClass.equals("boolean") ||
                attributeJavaClass.equals("string") ||
                attributeJavaClass.equals("float") ||
                attributeJavaClass.equals("double");
    }

    private boolean isPrimitiveValue(Object obj) {
        String javaClass = obj.getClass().getSimpleName().toLowerCase();
        return javaClass.startsWith("int") ||
                javaClass.startsWith("long") ||
                javaClass.equals("boolean") ||
                javaClass.equals("string") ||
                javaClass.equals("float") ||
                javaClass.equals("double");
    }

    private boolean isEnum(Attribute attribute) {
        String parentJavaClass = "";
        if (attribute.getJavaType().getSuperclass() != null) {
            parentJavaClass = attribute.getJavaType().getSuperclass().getSimpleName().toLowerCase();
        }
        return parentJavaClass.equals("enum");
    }

    private boolean isNull(Attribute attribute, Object val) {
        if (isPrimitive(attribute)) {
            String attributeJavaClass = attribute.getJavaType().getSimpleName().toLowerCase();
            if (attributeJavaClass.equals("string")) {
                String valObj = (String) val;
                return StringUtils.isBlank(valObj) || valObj.equalsIgnoreCase("null");
            } else {
                return val == null;
            }
        } else {
            return val == null;
        }
    }

}
