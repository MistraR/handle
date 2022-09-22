/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server.servletcontainer.servlets;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import net.cnri.util.StreamUtil;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

public class JsonParameterServletReq extends HttpServletRequestWrapper {
    JsonObject json;

    public JsonParameterServletReq(HttpServletRequest servletReq) throws IOException, JsonParseException {
        super(servletReq);
        String entity = StreamUtil.readFully(servletReq.getReader());
        if (entity.isEmpty()) return;
        JsonElement jsonElement = new JsonParser().parse(entity);
        if (jsonElement.isJsonObject()) json = jsonElement.getAsJsonObject();
        else throw new JsonParseException("Expected JSON object");
    }

    @Override
    public String getParameter(String name) {
        String res = super.getParameter(name);
        if (res != null) return res;
        if (json == null) return null;
        res = getStringProperty(name);
        return res;
    }

    private String getStringProperty(String name) {
        JsonElement property = json.get(name);
        if (property == null) return null;
        if (property.isJsonArray()) {
            JsonArray array = property.getAsJsonArray();
            if (array.size() > 0) return array.get(0).getAsString();
            else return null;
        } else {
            return property.getAsString();
        }
    }

    @Override
    public Enumeration<String> getParameterNames() {
        Enumeration<String> res = super.getParameterNames();
        if (json == null) return res;
        Set<String> set = new HashSet<>();
        while (res.hasMoreElements()) set.add(res.nextElement());
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            set.add(entry.getKey());
        }
        return Collections.enumeration(set);
    }

    @Override
    public String[] getParameterValues(String name) {
        String[] origRes = super.getParameterValues(name);
        if (json == null) return origRes;
        String[] props = getMultipleStringProperty(name);
        if (props == null) return origRes;
        if (origRes == null) return props;
        String[] augmentedRes = new String[origRes.length + props.length];
        System.arraycopy(origRes, 0, augmentedRes, 0, origRes.length);
        System.arraycopy(props, 0, augmentedRes, origRes.length, props.length);
        return augmentedRes;
    }

    private String[] getMultipleStringProperty(String name) {
        JsonElement property = json.get(name);
        if (property == null) return null;
        return stringArrayOfJsonElement(property);
    }

    private String[] stringArrayOfJsonElement(JsonElement property) {
        if (property.isJsonArray()) {
            JsonArray array = property.getAsJsonArray();
            if (array.size() > 0) {
                String[] res = new String[array.size()];
                for (int i = 0; i < res.length; i++) {
                    res[i] = array.get(i).getAsString();
                }
                return res;
            } else return null;
        } else {
            return new String[] { property.getAsString() };
        }
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        return new AbstractMap<String, String[]>() {
            Set<Map.Entry<String, String[]>> entrySet;

            @Override
            public Set<Map.Entry<String, String[]>> entrySet() {
                if (entrySet != null) return entrySet;
                Enumeration<String> queryNames = JsonParameterServletReq.super.getParameterNames();
                Set<String> keySet = new HashSet<>();
                entrySet = new HashSet<>();
                while (queryNames.hasMoreElements()) {
                    String name = queryNames.nextElement();
                    keySet.add(name);
                    entrySet.add(new AbstractMap.SimpleEntry<>(name, getParameterValues(name)));
                }
                if (json == null) return entrySet;
                for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                    String name = entry.getKey();
                    if (keySet.contains(name)) continue;
                    keySet.add(name);
                    entrySet.add(new AbstractMap.SimpleEntry<>(name, stringArrayOfJsonElement(entry.getValue())));
                }
                return entrySet;
            }
        };
    }
}
