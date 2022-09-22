/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.batch;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.security.InvalidParameterException;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

import net.handle.hdllib.*;
import net.handle.hdllib.trust.HandleSigner;
import net.handle.hdllib.trust.JsonWebSignature;

public class BatchUtil {

    public static SiteInfo getSite(String siteHandle, HandleResolver resolver) throws HandleException {
        if (siteHandle.contains(":")) {
            String[] tokens = siteHandle.split(":");
            int index = Integer.parseInt(tokens[0]);
            String handle = tokens[1];

            ResolutionRequest svcReq = new ResolutionRequest(Util.encodeString(handle), null, null, null);
            svcReq.authoritative = true;
            AbstractResponse response = resolver.processRequest(svcReq);
            BatchUtil.throwIfNotSuccess(response);
            ResolutionResponse resp;
            if (response instanceof ResolutionResponse) {
                resp = (ResolutionResponse) response;
            } else {
                throw new HandleException(HandleException.INTERNAL_ERROR, AbstractMessage.getResponseCodeMessage(response.responseCode));
            }
            HandleValue[] values = resp.getHandleValues();
            HandleValue siteHandleValue = getHandleValueAtIndex(values, index);
            if (siteHandleValue == null) {
                throw new InvalidParameterException("There is no siteInfo at index " + index + " on handle record " + handle);
            }
            SiteInfo site = new SiteInfo();
            try {
                Encoder.decodeSiteInfoRecord(siteHandleValue.getData(), 0, site);
            } catch (HandleException e) {
                throw new InvalidParameterException("Could not decode a siteInfo at index " + index + " on handle record " + handle);
            }
            return site;
        } else {
            ResolutionRequest svcReq = new ResolutionRequest(Util.encodeString(siteHandle), null, null, null);
            svcReq.authoritative = true;
            AbstractResponse response = resolver.processRequest(svcReq);
            BatchUtil.throwIfNotSuccess(response);
            ResolutionResponse resp;
            if (response instanceof ResolutionResponse) {
                resp = (ResolutionResponse) response;
            } else {
                throw new HandleException(HandleException.INTERNAL_ERROR, AbstractMessage.getResponseCodeMessage(response.responseCode));
            }
            HandleValue[] values = resp.getHandleValues();
            SiteInfo site = getFirstPrimarySite(values);
            if (site == null) site = getFirstPrimarySiteFromHserv(values, resolver);
            return site;
        }
    }

    public static HandleValue getHandleValueAtIndex(HandleValue[] handleValues, int index) {
        for (HandleValue value : handleValues) {
            if (value.getIndex() == index) {
                return value;
            }
        }
        return null;
    }

    public static SiteInfo getFirstPrimarySiteFromHserv(HandleValue[] values, HandleResolver resolver) throws HandleException {
        List<HandleValue> servs = BatchUtil.getValuesOfType(values,"HS_SERV");
        if (servs == null) return null;
        for (HandleValue serv : servs) {
            String siteHandle = serv.getDataAsString();
            ResolutionRequest svcReq = new ResolutionRequest(Util.encodeString(siteHandle), null, null, null);
            svcReq.authoritative = true;
            AbstractResponse response = resolver.processRequest(svcReq);
            ResolutionResponse resp;
            if (response instanceof ResolutionResponse) {
                resp = (ResolutionResponse) response;
                HandleValue[] siteValues = resp.getHandleValues();
                SiteInfo site = getFirstPrimarySite(siteValues);
                if (site != null) return site;
            }
        }
        return null;
    }

    public static SiteInfo getFirstPrimarySite(HandleValue[] values) {
        SiteInfo[] sites = Util.getSitesFromValues(values);
        if (sites == null) return null;
        for (SiteInfo site : sites) {
            if (site.isPrimary) {
                return site;
            }
        }
        return null;
    }

    public static List<String> listAllHandlesOnSite(SiteInfo site, HandleResolver resolver, AuthenticationInfo authInfo) throws HandleException {
        List<String> result = new ArrayList<>();
        ListPrefixesUtil listUtil = new ListPrefixesUtil(site, authInfo, resolver);
        List<String> allPrefixes = listUtil.getAllPrefixes();
        for (String prefix : allPrefixes) {
            List<String> handlesUnderPrefix = listHandles(prefix, site, resolver, authInfo);
            result.addAll(handlesUnderPrefix);
        }
        return result;
    }

    public static List<String> listPrefixesOnSite(SiteInfo site, HandleResolver resolver, AuthenticationInfo authInfo) throws HandleException {
        ListPrefixesUtil listUtil = new ListPrefixesUtil(site, authInfo, resolver);
        List<String> allPrefixes = listUtil.getAllPrefixes();
        return allPrefixes;
    }

    public static List<String> listHandles(String prefix, SiteInfo site, HandleResolver resolver, AuthenticationInfo authInfo) throws HandleException {
        ListHandlesUtil listUtil = new ListHandlesUtil(site, authInfo, resolver);
        List<String> result = listUtil.getAllHandles(prefix);
        return result;
    }

    public static List<String> getHandlesFromFile(String fileName) throws FileNotFoundException {
        Scanner sc = new Scanner(new File(fileName), "UTF-8");
        try {
            List<String> lines = new ArrayList<>();
            while (sc.hasNextLine()) {
                lines.add(sc.nextLine());
            }
            return lines;
        } finally {
            sc.close();
        }
    }

    public static List<String> getLinesFromFile(String fileName) throws FileNotFoundException {
        Scanner sc = new Scanner(new File(fileName), "UTF-8");
        try {
            List<String> lines = new ArrayList<>();
            while (sc.hasNextLine()) {
                lines.add(sc.nextLine());
            }
            return lines;
        } finally {
            sc.close();
        }
    }

    public static void writeHandlesToFile(List<String> handles, String fileName) throws IOException {
        FileWriter writer = new FileWriter(fileName);
        for (String handle : handles) {
            writer.write(handle + "\n");
        }
        writer.close();
    }

    public static void writeHandlesToConsole(List<String> handles) {
        for (String handle : handles) {
            System.out.println(handle);
        }
    }

    public static HandleValue[] addValue(HandleValue[] values, HandleValue value) {
        List<HandleValue> augmentedValues = new ArrayList<>();
        augmentedValues.addAll(Arrays.asList(values));
        augmentedValues.add(value);
        HandleValue[] result = augmentedValues.toArray(new HandleValue[0]);
        return result;
    }

    public static int getNextIndex(HandleValue[] values, int start) {
        int i = 0;
        HandleValue val;
        while (i < values.length) {
            val = values[i];
            if (val.getIndex() == start) {
                start++;
                i = 0;
            } else {
                i++;
            }
        }
        return start;
    }

    public static int lowestAvailableIndex(HandleValue[] values) {
        List<Integer> usedIndexes = new ArrayList<>();
        for (HandleValue value : values) {
            usedIndexes.add(value.getIndex());
        }
        return lowestAvailableIndex(usedIndexes);
    }

    public static int lowestAvailableIndex(List<Integer> usedIndexes) {
        if (usedIndexes.size() == 0) {
            return 1;
        }
        if (usedIndexes.size() == 1) {
            int index = usedIndexes.get(0);
            if (index == 0) throw new IllegalArgumentException("Handle Values may not have an index of zero");
            if (index > 1) {
                return 1;
            } else {
                return 2;
            }
        }
        Collections.sort(usedIndexes);
        for (int i = 0; i < usedIndexes.size(); i++) {
            int indexA = usedIndexes.get(i);
            if (i == 0) { //if is first item
                if (indexA == 0) throw new IllegalArgumentException("Handle Values may not have an index of zero");
                if (indexA > 1) {
                    return 1;
                }
            }
            if (usedIndexes.size() - 1 == i) { //if is last item
                return indexA + 1;
            }
            int indexB = usedIndexes.get(i + 1);
            if (indexB - indexA > 1) { //if there is a gap
                return indexA + 1;
            }
        }
        return -1; //should never get here
    }

    public static boolean hasHandleValueOfType(HandleValue[] values, String type) {
        for (HandleValue value : values) {
            byte[] typeBytes = value.getType();
            String valueType = Util.decodeString(typeBytes);
            if (type.equals(valueType)) {
                return true;
            }
        }
        return false;
    }

    public static List<HandleValue> getValuesOfType(HandleValue[] values, String type) {
        List<HandleValue> result = new ArrayList<>();
        for (HandleValue value : values) {
            byte[] typeBytes = value.getType();
            String valueType = Util.decodeString(typeBytes);
            if (type.equals(valueType)) {
                result.add(value);
            }
        }
        return result;
    }

    public static List<HandleValue> getValuesNotOfType(HandleValue[] values, String type) {
        List<HandleValue> result = new ArrayList<>();
        for (HandleValue value : values) {
            byte[] typeBytes = value.getType();
            String valueType = Util.decodeString(typeBytes);
            if (!type.equals(valueType)) {
                result.add(value);
            }
        }
        return result;
    }

    public static AbstractResponse addHandleValue(String handle, HandleValue value, HandleResolver resolver, AuthenticationInfo authInfo, SiteInfo site) throws HandleException {
        byte[] handleBytes = Util.encodeString(handle);
        AddValueRequest request = new AddValueRequest(handleBytes, value, authInfo);
        AbstractResponse response = resolver.sendRequestToSite(request, site);
        return response;
    }

    public static HandleValue[] resolveHandle(String handle, HandleResolver resolver, AuthenticationInfo authInfo) throws HandleException {
        byte[] handleBytes = Util.encodeString(handle);
        ResolutionRequest request = new ResolutionRequest(handleBytes, null, null, authInfo);
        if (authInfo != null) request.ignoreRestrictedValues = false;
        AbstractResponse response = resolver.processRequest(request);
        BatchUtil.throwIfNotSuccess(response);
        if (response instanceof ResolutionResponse) {
            ResolutionResponse resResponse = (ResolutionResponse) response;
            return resResponse.getHandleValues();
        } else {
            throw new HandleException(HandleException.INTERNAL_ERROR, AbstractMessage.getResponseCodeMessage(response.responseCode));
        }
    }

    public static HandleValue[] resolveHandleFromSite(String handle, HandleResolver resolver, AuthenticationInfo authInfo, SiteInfo site) throws HandleException {
        byte[] handleBytes = Util.encodeString(handle);
        ResolutionRequest request = new ResolutionRequest(handleBytes, null, null, authInfo);
        if (authInfo != null) request.ignoreRestrictedValues = false;
        AbstractResponse response = resolver.sendRequestToSite(request, site);
        BatchUtil.throwIfNotSuccess(response);
        if (response instanceof ResolutionResponse) {
            ResolutionResponse resResponse = (ResolutionResponse) response;
            return resResponse.getHandleValues();
        } else {
            throw new HandleException(HandleException.INTERNAL_ERROR, AbstractMessage.getResponseCodeMessage(response.responseCode));
        }
    }

    public static AbstractResponse addAliasToHandleRecord(String handle, String alias, int index, HandleResolver resolver, AuthenticationInfo authInfo, SiteInfo site) throws HandleException {
        HandleValue aliasValue = new HandleValue(index, Util.encodeString("HS_ALIAS"), Util.encodeString(alias));
        AbstractResponse response = addHandleValue(handle, aliasValue, resolver, authInfo, site);
        return response;
    }

    public static AbstractResponse modifyHandleValue(String handle, HandleValue value, HandleResolver resolver, AuthenticationInfo authInfo, SiteInfo site) throws HandleException {
        byte[] handleBytes = Util.encodeString(handle);
        ModifyValueRequest request = new ModifyValueRequest(handleBytes, value, authInfo);
        AbstractResponse response = resolver.sendRequestToSite(request, site);
        return response;
    }

    public static AbstractResponse updateEntireHandleRecord(String handle, List<HandleValue> values, HandleResolver resolver, AuthenticationInfo authInfo, SiteInfo site) throws HandleException {
        byte[] handleBytes = Util.encodeString(handle);
        CreateHandleRequest request = new CreateHandleRequest(handleBytes, values.toArray(new HandleValue[values.size()]), authInfo);
        request.overwriteWhenExists = true;
        AbstractResponse response = resolver.sendRequestToSite(request, site);
        return response;
    }

    public static AbstractResponse removeValueRequest(String handle, HandleValue value, HandleResolver resolver, AuthenticationInfo authInfo, SiteInfo site) throws HandleException {
        int indexToRemove = value.getIndex();
        byte[] handleBytes = Util.encodeString(handle);
        RemoveValueRequest request = new RemoveValueRequest(handleBytes, indexToRemove, authInfo);
        AbstractResponse response = resolver.sendRequestToSite(request, site);
        return response;
    }

    public static AbstractResponse removeValuesAtIndices(String handle, int[] indicesToRemove, HandleResolver resolver, AuthenticationInfo authInfo, SiteInfo site) throws HandleException {
        byte[] handleBytes = Util.encodeString(handle);
        RemoveValueRequest request = new RemoveValueRequest(handleBytes, indicesToRemove, authInfo);
        AbstractResponse response = resolver.sendRequestToSite(request, site);
        return response;
    }

    @SuppressWarnings("deprecation")
    public static HandleValue[] signHandleValues(HandleValue[] values, String handleToCreate, String handleOfSigner, int indexOfSigner, PrivateKey privateKeyOfSigner) throws Exception {
        // assuming index 400 and index 401 are not used in values
        HandleValue digestsValue = net.handle.hdllib.HandleSignature.createDigestsValue(400, handleToCreate, values);
        ValueReference signer = new ValueReference(Util.encodeString(handleOfSigner), indexOfSigner);
        String alg = null; // The library will pick an appropriate algorithm, either SHA1withDSA or SHA256withRSA
        HandleValue signatureValue = net.handle.hdllib.HandleSignature.createSignatureValue(401, handleToCreate, signer, alg, privateKeyOfSigner, digestsValue);
        List<HandleValue> augmentedValues = new ArrayList<>();
        augmentedValues.addAll(Arrays.asList(values));
        augmentedValues.add(digestsValue);
        augmentedValues.add(signatureValue);
        values = augmentedValues.toArray(new HandleValue[0]);
        return values;
    }

    public static HandleValue[] signHandleValuesWithJws(HandleValue[] values, String handleToSign, String handleOfSigner, int indexOfSigner, PrivateKey privateKeyOfSigner, List<String> chain) throws Exception {
        // assuming index 401 is not used in values
        List<HandleValue> valuesList = new ArrayList<>();
        valuesList.addAll(Arrays.asList(values));

        long ONE_DAY = 1000 * 60 * 60 * 24;
        long ONE_YEAR = ONE_DAY * 366;

        HandleSigner handleSigner = new HandleSigner();
        long now = System.currentTimeMillis();
        long notBefore = now - ONE_DAY;
        long expiration = now + ONE_YEAR * 2;
        ValueReference signer = new ValueReference(handleOfSigner, indexOfSigner);
        JsonWebSignature jws = handleSigner.signHandleValues(handleToSign, valuesList, signer, privateKeyOfSigner, chain, notBefore / 1000, expiration / 1000);

        HandleValue signatureValue = new HandleValue(401, Util.encodeString("HS_SIGNATURE"), Util.encodeString(jws.serialize()));
        valuesList.add(signatureValue);
        values = valuesList.toArray(new HandleValue[0]);
        return values;
    }

    public static HandleValue[] createExampleHandleValues(@SuppressWarnings("unused") String handle, String url, String adminHandle, int adminIndex) {
        HandleValue[] values = new HandleValue[2];
        values[0] = new HandleValue(2, Util.encodeString("URL"), Util.encodeString(url));
        values[1] = new HandleValue();
        values[1].setIndex(100);
        values[1].setType(Common.ADMIN_TYPE);
        values[1].setData(Encoder.encodeAdminRecord(new AdminRecord(Util.encodeString(adminHandle), adminIndex, true, // addHandle
            true, // deleteHandle
            true, // addNA
            true, // deleteNA
            true, // readValue
            true, // modifyValue
            true, // removeValue
            true, // addValue
            true, // modifyAdmin
            true, // removeAdmin
            true, // addAdmin
            true // listHandles
        )));
        return values;
    }

    public static AbstractResponse deleteHandleRecord(String handle, HandleResolver resolver, AuthenticationInfo authInfo, SiteInfo site) throws HandleException {
        byte[] handleBytes = Util.encodeString(handle);
        DeleteHandleRequest request = new DeleteHandleRequest(handleBytes, authInfo);
        AbstractResponse response = resolver.sendRequestToSite(request, site);
        return response;
    }

    public static AbstractResponse createHandleRecord(String handle, HandleValue[] values, HandleResolver resolver, AuthenticationInfo authInfo, SiteInfo site) throws HandleException {
        byte[] handleBytes = Util.encodeString(handle);
        CreateHandleRequest request = new CreateHandleRequest(handleBytes, values, authInfo);
        AbstractResponse response = resolver.sendRequestToSite(request, site);
        return response;
    }

    public static AbstractResponse updateEntireHandleRecord(String handle, HandleValue[] values, HandleResolver resolver, AuthenticationInfo authInfo, SiteInfo site) throws HandleException {
        byte[] handleBytes = Util.encodeString(handle);
        CreateHandleRequest request = new CreateHandleRequest(handleBytes, values, authInfo);
        request.overwriteWhenExists = true;
        AbstractResponse response = resolver.sendRequestToSite(request, site);
        return response;
    }

    public static AbstractResponse unhomePrefix(String prefix, HandleResolver resolver, AuthenticationInfo authInfo, SiteInfo site) throws HandleException {
        byte[] prefixBytes = Util.encodeString(prefix);
        UnhomeNaRequest request = new UnhomeNaRequest(prefixBytes, authInfo);
        AbstractResponse response = resolver.sendRequestToSite(request, site);
        return response;
    }

    public static AbstractResponse homePrefix(String prefix, HandleResolver resolver, AuthenticationInfo authInfo, SiteInfo site) throws HandleException {
        byte[] prefixBytes = Util.encodeString(prefix);
        HomeNaRequest request = new HomeNaRequest(prefixBytes, authInfo);
        AbstractResponse response = resolver.sendRequestToSite(request, site);
        return response;
    }

    public static void throwIfNotSuccess(AbstractResponse response) throws HandleException {
        if (response.responseCode != AbstractMessage.RC_SUCCESS) {
            throw new HandleException(HandleException.INTERNAL_ERROR, response.toString());
        }
    }

}
