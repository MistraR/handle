/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.api;

import net.handle.hdllib.HandleException;
import net.handle.hdllib.HandleValue;

public interface HSAdapter {

    /**
     * Adds new handle records. </br> <b> Note: </b>
     * <li>The administrative priveleges have to be valid for this method to
     * perform without any exception</li>
     *
     * @param handle
     *            The handle into which new values are to be added
     * @param values
     *            The array of handle values to deposit
     */
    public void addHandleValues(String handle, HandleValue[] values) throws HandleException;

    /**
     * Creates an administrative record with the adminHandle and adminIndex at
     * the index. Note this does not get added to any handle. Also, the
     * administrator has complete permissions. For the types of permissions,
     * please refer to the Handle.net Technical Manual.
     *
     * @param adminHandle
     *            The admin handle for this handle value
     * @param keyIndex
     *            The index of the public key in the admin Handle
     * @param index
     *            Which index to put this information at.
     * @return A new HandleValue containing the admin value
     * @throws HandleException
     */
    public HandleValue createAdminValue(String adminHandle, int keyIndex, int index) throws HandleException;

    /**
     * Creates a new handle. If the handle already exists, the method will throw
     * an exception. The proper course of action is then to delete the handle
     * and call the method again.
     *
     * @param handle
     *            The handle to create
     * @param values
     *            An array of handle values to add to the handle. <b>Note:</b>
     *            <b> Note: </b>
     *            <li>It is important to add admin handle value in order to
     *            administer this handle at a later point.</li>
     *            <li>The administrative priveleges have to be valid for this
     *            method to perform without any exception</li>
     * @exception HandleException Describes
     *                the error that occured in the process of creating the
     *                handle.
     *
     */
    public void createHandle(String handle, HandleValue[] values) throws HandleException;

    /**
     * Creates a new handle value. Note this does not get added to any handle.
     * The default permissions are adminRead=true, adminWrite=true,
     * publicRead=true, and publicWrite=false. Override the permissions once the
     * HandleValue is created for enforcing different permissions.
     *
     * @param index
     *            Which index to put this information at.
     * @param type
     *            The type of the handle value
     * @param data
     *            The data for this handle value Otherwise not.
     * @throws HandleException
     */
    public HandleValue createHandleValue(int index, String type, String data) throws HandleException;

    /**
     * Deletes an existing Handle from the handle server. </br> <b> Note: </b>
     * <li>The administrative priveleges have to be valid for this method to
     * perform without any exception</li>
     *
     * @param handle
     *            The handle to delete.
     *
     */
    public void deleteHandle(String handle) throws HandleException;

    /**
     * Deletes a specific set of handle values in a Handle. </br> <b> Note: </b>
     * <li>The administrative priveleges have to be valid for this method to
     * perform without any exception</li>
     *
     * @param handle
     *            The Handle that we want to delete values from
     * @param values
     *            An array of handle values to delete.
     * @exception HandleException Describes
     *                the error that occured while executing the method.
     *
     */
    public void deleteHandleValues(String handle, HandleValue[] values) throws HandleException;

    /**
     * Resolves a handle and returns a set of handle values that satisfy the
     * type filter specified. If the resolution is to retrieve all handle
     * values, specify null for both filter and indexes. If the administrative
     * priveleges are applicable, the restricted values will also be returned.
     *
     * @param handle
     *            The value of the handle to resolve
     * @param types
     *            The types of the handle values that we are looking for.
     * @param auth Whether to perform an authoritative resolution
     * @exception HandleException Describes
     *                the error in resolution
     */
    public HandleValue[] resolveHandle(String handle, String[] types, int[] indexes, boolean auth) throws HandleException;

    /**
     * Resolves a handle and returns a set of handle values that satisfy the
     * type filter specified. If the resolution is to retrieve all handle
     * values, specify null for both filter and indexes. If the administrative
     * priveleges are applicable, the restricted values will also be returned.
     * Also, the resolution request is not authoritative.
     *
     * @param handle
     *            The value of the handle to resolve
     * @param types
     *            The types of the handle values that we are looking for.
     * @exception HandleException Describes
     *                the error in resolution
     */
    public HandleValue[] resolveHandle(String handle, String[] types, int[] indexes) throws HandleException;

    /**
     * Set how long to wait for responses to TCP and HTTP requests.
     * @param newTcpTimeout Milliseconds to use for timeout.
     */
    public void setTcpTimeout(int newTcpTimeout);

    /**
     * Get how long to wait for responses to TCP and HTTP requests.
     */
    public int getTcpTimeout();

    /**
     * Adds and prioritizes the UDP for communication with the Handle server.
     * @param useUDP
     *              The boolean flag that specifies the use of UDP.
     */
    public void setUseUDP(boolean useUDP);

    /**
     * Updates the specified data handle values. </br> <b> Note: </b>
     * <li>Make sure that the index value is specified in the array of handle
     * values or else this method will not work well.</li>
     * <li>The administrative priveleges have to be valid for this method to
     * perform without any exception</li>
     *
     * @param handle
     * @param values
     */
    public void updateHandleValues(String handle, HandleValue[] values) throws HandleException;

}
