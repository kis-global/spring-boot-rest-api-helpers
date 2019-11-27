package springboot.rest.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class JSON {

    public static JSONObject toJsonObject(String str) {
        JSONObject jsonObj = new JSONObject(str);
        return jsonObj;
    }

    public static JSONArray toJsonArray(String str) {
        JSONArray jsonArr = new JSONArray(str);
        return jsonArr;
    }

    public static List toList(JSONArray jsonArray) {
        return IntStream.range(0,jsonArray.length()).mapToObj(i->jsonArray.get(i)).collect(Collectors.toList());
    }

    public static String toJsonString(Object obj) {
        ObjectMapper mapper = new ObjectMapper();

        //Object to JSON in String
        String jsonString = "";
        try {
            jsonString = mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return jsonString;
    }

    public static <T> T toObject(String jsonString, Class<T> clazz) {
        //JSON from String to Object
        ObjectMapper mapper = new ObjectMapper();
        T obj = null;
        try {
            obj = mapper.readValue(jsonString, clazz);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return obj;
    }

    public static boolean isValid(final String json) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
        boolean valid;
        try {
            objectMapper.readTree(json);
            valid = true;
        } catch (IOException e) {
            valid = false;
        }
        return valid;
    }
}
