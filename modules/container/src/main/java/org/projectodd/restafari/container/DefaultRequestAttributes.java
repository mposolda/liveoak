package org.projectodd.restafari.container;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.projectodd.restafari.spi.RequestAttributes;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class DefaultRequestAttributes implements RequestAttributes {

    private Map<String, Object> attributesMap;

    public DefaultRequestAttributes() {};

    @Override
    public Collection<String> getAttributeNames() {
        if (attributesMap == null) {
            return null;
        } else {
            return attributesMap.keySet();
        }
    }

    @Override
    public Object getAttribute(String attributeName) {
        if (attributesMap == null) {
            return null;
        } else {
            return attributesMap.get(attributeName);
        }
    }

    @Override
    public void setAttribute(String attributeName, Object attributeValue) {
        if (attributesMap == null) {
            attributesMap = new HashMap<>();
        }
        attributesMap.put(attributeName, attributeValue);
    }

    @Override
    public Object removeAttribute(String attributeName) {
        if (attributesMap == null) {
            return null;
        } else {
            return attributesMap.remove(attributeName);
        }
    }
}
