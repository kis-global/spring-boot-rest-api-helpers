package springboot.rest.controllerAdvices;

import lombok.NonNull;
import lombok.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.bind.annotation.ControllerAdvice;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.lang.reflect.Field;

//https://stackoverflow.com/a/40333275/986160
@ControllerAdvice
public class WrapperAdvice implements ResponseBodyAdvice {

    @Autowired
    Environment env;

    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType, Class selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        String apiPrefix = env.getProperty("spring-boot-rest-api-helpers.api-prefix");
        if (!request.getURI().toString().contains(apiPrefix)) {
            return body;
        }
        if (body instanceof PageImpl) {
            return body;
        }
        if (isArray(body)) {
            return new Wrapper(Arrays.asList(body));
        }
        if (body instanceof Iterable) {
            return new Wrapper((Iterable) body);
        }
        if (!((body instanceof byte[]) ||
              (body instanceof InputStreamResource) ||
              (body instanceof LinkedHashMap && ((LinkedHashMap) body).containsKey("exception")))) {
            return new SingleObjectWrapper<>(body);
        }
        return body;
    }

    @Value
    private class Wrapper {
        private final @NonNull Iterable content;
    }

    @Value
    private class SingleObjectWrapper<T> {
        private final ArrayList<T> content = new ArrayList<>();
        public SingleObjectWrapper(@NonNull T obj) {
            this.content.add(obj);
        }
    }

    public static boolean isArray(Object obj) {
        return obj != null && obj.getClass().isArray() && !(obj instanceof byte[]);
    }

}
