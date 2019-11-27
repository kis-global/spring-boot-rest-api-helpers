package springboot.rest.controllerAdvices;

import lombok.NonNull;
import lombok.Value;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.bind.annotation.ControllerAdvice;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//https://stackoverflow.com/a/40333275/986160
@ControllerAdvice
public class WrapperAdvice implements ResponseBodyAdvice {

    static final Logger LOG = LoggerFactory.getLogger(WrapperAdvice.class);

    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType, Class selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof PageImpl) {
            LOG.debug("PageImpl not wrapped "+body.getClass().getName());
            return body;
        }
        if (isArray(body)) {
            LOG.debug("Wrap Arrays.asList "+body.getClass().getName());
            return new Wrapper(Arrays.asList(body));
        }
        if (body instanceof Iterable) {
            LOG.debug("Wrap Iterable !PageImpl "+body.getClass().getName());
            return new Wrapper((Iterable) body);
        }
        if (!((body instanceof byte[]) ||
              (body instanceof InputStreamResource) ||
              (body instanceof LinkedHashMap && ((LinkedHashMap)(body)).containsKey("exception")))) {
            LOG.debug("Single object wrapper "+body.getClass().getName());
            return new SingleObjectWrapper<>(body);
        }
        LOG.debug("Not wrapped "+body.getClass().getName());
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
        return obj != null && (obj.getClass().isArray() || obj instanceof Iterable) && !(obj instanceof byte[]);
    }

}
