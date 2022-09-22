/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server.servletcontainer.servlets;

import java.io.*;
import java.util.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.handle.hdllib.AbstractMessage;
import net.handle.hdllib.AbstractRequest;
import net.handle.hdllib.AbstractResponse;
import net.handle.hdllib.AddValueRequest;
import net.handle.hdllib.AuthenticationInfo;
import net.handle.hdllib.CreateHandleRequest;
import net.handle.hdllib.DeleteHandleRequest;
import net.handle.hdllib.ErrorResponse;
import net.handle.hdllib.GsonUtility;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.HandleValue;
import net.handle.hdllib.ListHandlesRequest;
import net.handle.hdllib.RemoveValueRequest;
import net.handle.hdllib.ResolutionRequest;
import net.handle.hdllib.ResponseMessageCallback;
import net.handle.hdllib.Util;
import net.handle.server.servletcontainer.support.LoggingResponseMessageCallbackWrapper;

public class HandleJsonRestApiServlet extends BaseHandleRequestProcessingServlet {
    {
        allowString = "GET, HEAD, POST, PUT, DELETE, TRACE, OPTIONS";
    }

    private static byte[][] getTypes(HttpServletRequest servletReq) {
        String[] typeStrings = servletReq.getParameterValues("type");
        byte[][] types = null;
        if (typeStrings != null && typeStrings.length > 0) {
            types = new byte[typeStrings.length][];
            for (int i = 0; i < typeStrings.length; i++) {
                types[i] = Util.encodeString(typeStrings[i]);
            }
        }
        return types;
    }

    @Override
    protected void doGet(HttpServletRequest servletReq, HttpServletResponse servletResp) throws ServletException, IOException {
        String handle = getPath(servletReq);
        if (handle.isEmpty()) {
            String prefix = servletReq.getParameter("prefix");
            if (prefix != null) listHandles(prefix, servletReq, servletResp);
            else emptyHandleError(servletReq, servletResp);
        } else doOneHandleGet(servletReq, servletResp);
    }

    private void emptyHandleError(HttpServletRequest servletReq, HttpServletResponse servletResp) throws IOException {
        AbstractResponse resp = new ErrorResponse(AbstractMessage.OC_RESERVED, AbstractMessage.RC_INVALID_HANDLE, Util.encodeString("Empty handle invalid"));
        processResponse(servletReq, servletResp, null, resp);
    }

    private void doOneHandleGet(HttpServletRequest servletReq, HttpServletResponse servletResp) throws IOException {
        ResolutionRequest req;
        AbstractResponse resp;
        try {
            req = getResolutionRequest(servletReq);
            resp = processRequest(servletReq, req);
        } catch (Exception e) {
            req = null;
            resp = errorResponseFromException(e);
        }
        processResponse(servletReq, servletResp, req, resp);
    }

    private static ResolutionRequest getResolutionRequest(HttpServletRequest servletReq) throws Exception {
        String handle = getPath(servletReq);
        byte[][] types = getTypes(servletReq);
        int[] indexes = getIndexes(servletReq);
        AuthenticationInfo authInfo = getAuthenticationInfo(servletReq);
        ResolutionRequest resReq = new ResolutionRequest(Util.encodeString(handle), types, indexes, authInfo);
        if (servletReq.getParameter("publicOnly") != null) {
            resReq.ignoreRestrictedValues = getBooleanParameter(servletReq, "publicOnly");
        } else {
            resReq.ignoreRestrictedValues = authInfo == null;
        }
        resReq.authoritative = getBooleanParameter(servletReq, "auth");
        return resReq;
    }

    @Override
    protected void doPut(HttpServletRequest servletReq, HttpServletResponse servletResp) throws ServletException, IOException {
        AbstractRequest req;
        AbstractResponse resp;
        try {
            checkContentType(servletReq);
            req = getCreateOrAddRequest(servletReq);
            resp = processRequest(servletReq, req);
        } catch (Exception e) {
            req = null;
            resp = errorResponseFromException(e);
        }
        processResponse(servletReq, servletResp, req, resp);
    }

    private void checkContentType(HttpServletRequest servletReq) throws Exception {
        if (!hasJsonEntity(servletReq)) {
            // TODO better to return 415 Unsupported Media Type instead of 400
            throw new Exception("Unsupported media type " + servletReq.getContentType());
        }
    }

    private AbstractRequest getCreateOrAddRequest(HttpServletRequest servletReq) throws Exception {
        String handle = getPath(servletReq);
        int[] indexes = null;
        boolean indexesVarious = "various".equals(servletReq.getParameter("index"));
        if (!indexesVarious) indexes = getIndexes(servletReq);
        JsonElement valuesJson = new JsonParser().parse(servletReq.getReader());
        HandleValue[] values;
        if (valuesJson.isJsonArray()) {
            values = GsonUtility.getGson().fromJson(valuesJson, HandleValue[].class);
        } else if (valuesJson.isJsonObject()) {
            JsonObject valuesJsonObject = valuesJson.getAsJsonObject();
            if (valuesJsonObject.has("values")) {
                throwIfHandlesDoNotMatch(handle, valuesJson);
                values = GsonUtility.getGson().fromJson(valuesJsonObject.get("values"), HandleValue[].class);
            } else if (looksLikeHandleValue(valuesJsonObject)) {
                HandleValue value = GsonUtility.getGson().fromJson(valuesJsonObject, HandleValue.class);
                values = new HandleValue[] { value };
            } else {
                throw new Exception("Invalid JSON in PUT request");
            }
        } else {
            throw new Exception("Invalid JSON in PUT request");
        }
        if (values == null) values = new HandleValue[0];
        AbstractRequest req;
        if (!indexesVarious && indexes == null) {
            req = new CreateHandleRequest(Util.encodeString(handle), values, getAuthenticationInfo(servletReq));
        } else {
            if (!indexesVarious) throwIfIndexesDoNotMatch(indexes, values);
            req = new AddValueRequest(Util.encodeString(handle), values, getAuthenticationInfo(servletReq));
        }
        String overwriteParam = servletReq.getParameter("overwrite");
        if (overwriteParam == null) {
            req.overwriteWhenExists = "PUT".equals(servletReq.getMethod());
        } else {
            req.overwriteWhenExists = Boolean.parseBoolean(overwriteParam);
        }
        String mintNewSuffix = servletReq.getParameter("mintNewSuffix");
        if (mintNewSuffix != null) {
            req.mintNewSuffix = Boolean.parseBoolean(mintNewSuffix);
        }
        return req;
    }

    static boolean looksLikeHandleValue(JsonObject valueJsonObject) {
        if (!valueJsonObject.has("index")) return false;
        if (!valueJsonObject.has("type")) return false;
        if (!valueJsonObject.has("data")) return false;
        return true;
    }

    private void throwIfHandlesDoNotMatch(String handle, JsonElement valuesJson) throws Exception {
        JsonElement handleFromJson = valuesJson.getAsJsonObject().get("handle");
        if (handleFromJson != null && (!handleMatches(handle, handleFromJson.getAsString()))) {
            throw new Exception("Mismatched handle in PUT request");
        }
    }

    private static void throwIfIndexesDoNotMatch(int[] indexes, HandleValue[] values) throws Exception {
        Set<Integer> indexesFromValues = new HashSet<>();
        for (HandleValue value : values) {
            indexesFromValues.add(Integer.valueOf(value.getIndex()));
        }
        Set<Integer> indexesFromQuery = new HashSet<>();
        for (int index : indexes) {
            indexesFromQuery.add(Integer.valueOf(index));
        }
        if (indexesFromQuery.size() != indexesFromValues.size() || indexesFromQuery.retainAll(indexesFromValues)) {
            throw new Exception("Mismatched indexes in PUT request");
        }
    }

    private boolean handleMatches(String h1, String h2) {
        if (caseSensitive) {
            return Util.equalsPrefixCI(Util.encodeString(h1), Util.encodeString(h2));
        } else {
            return Util.equalsCI(Util.encodeString(h1), Util.encodeString(h2));
        }
    }

    @Override
    protected void doDelete(HttpServletRequest servletReq, HttpServletResponse servletResp) throws ServletException, IOException {
        AbstractRequest req;
        AbstractResponse resp;
        try {
            req = getDeleteRequest(servletReq);
            resp = processRequest(servletReq, req);
        } catch (Exception e) {
            req = null;
            resp = errorResponseFromException(e);
        }
        processResponse(servletReq, servletResp, req, resp);
    }

    private static AbstractRequest getDeleteRequest(HttpServletRequest servletReq) throws Exception {
        String handle = getPath(servletReq);
        int[] indexes = getIndexes(servletReq);
        AbstractRequest req;
        if (indexes == null) {
            req = new DeleteHandleRequest(Util.encodeString(handle), getAuthenticationInfo(servletReq));
        } else {
            req = new RemoveValueRequest(Util.encodeString(handle), indexes, getAuthenticationInfo(servletReq));
        }
        return req;
    }

    private void listHandles(String prefix, HttpServletRequest servletReq, HttpServletResponse servletResp) throws IOException {
        try {
            ListHandlesRequest listReq = getListHandlesRequest(prefix, servletReq);
            ListCallback listCallback = new ListCallback();
            listCallback.page = getIntegerParameter(servletReq, "page", -1);
            listCallback.pageSize = getIntegerParameter(servletReq, "pageSize", -1);
            listReq.certify = getBooleanParameter(servletReq, "cert");
            ResponseMessageCallback callbackWrapper = listCallback;
            if (handleServer != null) callbackWrapper = new LoggingResponseMessageCallbackWrapper(listCallback, handleServer, handleServer.logHttpAccesses(), listReq, getRemoteInetAddress(servletReq), "HDLApi");
            try {
                requestHandler.processRequest(listReq, getRemoteInetAddress(servletReq), callbackWrapper);
            } catch (HandleException e) {
                listCallback.unexpectedResponse = HandleException.toErrorResponse(listReq, e);
            }
            if (listCallback.unexpectedResponse != null) {
                processResponse(servletReq, servletResp, listReq, listCallback.unexpectedResponse);
            } else {
                listCallback.processListHandlesResponse(servletReq, servletResp, listReq, listCallback.handles, listCallback.totalCount);
            }
        } catch (IllegalArgumentException e) {
            processResponse(servletReq, servletResp, null, errorResponseFromException(e));
        }
    }

    private static ListHandlesRequest getListHandlesRequest(String prefix, HttpServletRequest servletReq) throws IllegalArgumentException, NumberFormatException {
        //if(!prefix.toUpperCase().startsWith("0.NA/")) prefix = "0.NA/" + prefix;
        return new ListHandlesRequest(Util.encodeString(prefix), getAuthenticationInfo(servletReq));
    }
}
