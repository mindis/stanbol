/**
 * 
 */
package org.apache.stanbol.contenthub.web.writers;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

import org.apache.commons.io.IOUtils;
import org.apache.stanbol.contenthub.servicesapi.Constants;
import org.apache.stanbol.contenthub.servicesapi.search.featured.SearchResult;
import org.apache.stanbol.contenthub.web.util.JSONUtils;
import org.apache.stanbol.contenthub.web.util.RestUtil;
import org.codehaus.jettison.json.JSONException;

/**
 * 
 * @author anil.sinaci
 * 
 */
@Provider
public class SearchResultWriter implements MessageBodyWriter<SearchResult> {

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        String mediaTypeString = mediaType.getType() + '/' + mediaType.getSubtype();
        return SearchResult.class.isAssignableFrom(type) && RestUtil.supportedMediaTypes.contains(mediaTypeString);
    }

    @Override
    public long getSize(SearchResult t,
                        Class<?> type,
                        Type genericType,
                        Annotation[] annotations,
                        MediaType mediaType) {
        return -1;
    }

    @Override
    public void writeTo(SearchResult t,
                        Class<?> type,
                        Type genericType,
                        Annotation[] annotations,
                        MediaType mediaType,
                        MultivaluedMap<String,Object> httpHeaders,
                        OutputStream entityStream) throws IOException, WebApplicationException {
        String mediaTypeString = mediaType.getType() + '/' + mediaType.getSubtype();
        String encoding = mediaType.getParameters().get("charset");
        if (encoding == null) {
            encoding = Constants.DEFAULT_ENCODING;
        }
        if (APPLICATION_JSON.equals(mediaTypeString)) {
            try {
                String jsonstr = JSONUtils.createJSONString(t);
                IOUtils.write(jsonstr, entityStream, encoding);
            } catch (JSONException e) {
                throw new WebApplicationException(e, Status.INTERNAL_SERVER_ERROR);
            }
        }
    }
    
}
