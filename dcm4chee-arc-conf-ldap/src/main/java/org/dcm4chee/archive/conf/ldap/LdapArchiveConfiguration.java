/*
 * **** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * J4Care.
 * Portions created by the Initial Developer are Copyright (C) 2013
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * **** END LICENSE BLOCK *****
 */

package org.dcm4chee.archive.conf.ldap;

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.ldap.LdapDicomConfigurationExtension;
import org.dcm4che3.conf.ldap.LdapUtils;
import org.dcm4che3.conf.ldap.imageio.LdapCompressionRulesConfiguration;
import org.dcm4che3.data.ValueSelector;
import org.dcm4che3.imageio.codec.CompressionRules;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.util.TagUtils;
import org.dcm4chee.archive.conf.*;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.*;
import java.net.URI;
import java.security.cert.CertificateException;
import java.util.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2015
 */
class LdapArchiveConfiguration extends LdapDicomConfigurationExtension {

    @Override
    protected void storeTo(Device device, Attributes attrs) {
        ArchiveDeviceExtension ext = device.getDeviceExtension(ArchiveDeviceExtension.class);
        if (ext == null)
            return;

        attrs.get("objectclass").add("dcmArchiveDevice");
        LdapUtils.storeNotNull(attrs, "dcmFuzzyAlgorithmClass", ext.getFuzzyAlgorithmClass());
        LdapUtils.storeNotNull(attrs, "dcmStorageID", ext.getStorageID());
        LdapUtils.storeNotNull(attrs, "dcmBulkDataSpoolDirectory", ext.getBulkDataSpoolDirectory());
        LdapUtils.storeNotNull(attrs, "dcmQueryRetrieveViewID", ext.getQueryRetrieveViewID());
        LdapUtils.storeNotDef(attrs, "dcmQueryMatchUnknown", ext.isQueryMatchUnknown(), true);
        LdapUtils.storeNotDef(attrs, "dcmPersonNameComponentOrderInsensitiveMatching",
                ext.isPersonNameComponentOrderInsensitiveMatching(), false);
    }

    @Override
    protected void loadFrom(Device device, Attributes attrs) throws NamingException, CertificateException {
        if (!LdapUtils.hasObjectClass(attrs, "dcmArchiveDevice"))
            return;

        ArchiveDeviceExtension ext = new ArchiveDeviceExtension();
        device.addDeviceExtension(ext);
        ext.setFuzzyAlgorithmClass(LdapUtils.stringValue(attrs.get("dcmFuzzyAlgorithmClass"), null));
        ext.setStorageID(LdapUtils.stringValue(attrs.get("dcmStorageID"), null));
        ext.setBulkDataSpoolDirectory(LdapUtils.stringValue(attrs.get("dcmBulkDataSpoolDirectory"), null));
        ext.setQueryRetrieveViewID(LdapUtils.stringValue(attrs.get("dcmQueryRetrieveViewID"), null));
        ext.setQueryMatchUnknown(LdapUtils.booleanValue(attrs.get("dcmQueryMatchUnknown"), true));
        ext.setPersonNameComponentOrderInsensitiveMatching(
                LdapUtils.booleanValue(attrs.get("dcmPersonNameComponentOrderInsensitiveMatching"), false));
    }

    @Override
    protected void storeDiffs(Device prev, Device device, List<ModificationItem> mods) {
        ArchiveDeviceExtension aa = prev.getDeviceExtension(ArchiveDeviceExtension.class);
        ArchiveDeviceExtension bb = device.getDeviceExtension(ArchiveDeviceExtension.class);
        if (aa == null || bb == null)
            return;

        LdapUtils.storeDiff(mods, "dcmFuzzyAlgorithmClass", aa.getFuzzyAlgorithmClass(), bb.getFuzzyAlgorithmClass());
        LdapUtils.storeDiff(mods, "dcmStorageID", aa.getStorageID(), bb.getStorageID());
        LdapUtils.storeDiff(mods, "dcmBulkDataSpoolDirectory",
                aa.getBulkDataSpoolDirectory(), bb.getBulkDataSpoolDirectory());
        LdapUtils.storeDiff(mods, "dcmQueryRetrieveViewID", aa.getQueryRetrieveViewID(), bb.getQueryRetrieveViewID());
        LdapUtils.storeDiff(mods, "dcmQueryMatchUnknown", aa.isQueryMatchUnknown(), bb.isQueryMatchUnknown(), true);
        LdapUtils.storeDiff(mods, "dcmPersonNameComponentOrderInsensitiveMatching",
                aa.isPersonNameComponentOrderInsensitiveMatching(),
                bb.isPersonNameComponentOrderInsensitiveMatching(),
                false);
    }

    @Override
    protected void storeChilds(String deviceDN, Device device)
            throws NamingException, ConfigurationException {
        ArchiveDeviceExtension arcDev = device
                .getDeviceExtension(ArchiveDeviceExtension.class);
        if (arcDev == null)
            return;

        storeAttributeFilter(deviceDN, arcDev);
        storeStorageDescriptors(deviceDN, arcDev);
        storeCompressionRules(arcDev.getCompressionRules(), deviceDN);
        storeQueryRetrieveViews(deviceDN, arcDev);
    }

    @Override
    protected void loadChilds(Device device, String deviceDN)
            throws NamingException, ConfigurationException {
        ArchiveDeviceExtension arcdev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        if (arcdev == null)
            return;

        loadAttributeFilters(arcdev, deviceDN);
        loadStorageDescriptors(arcdev, deviceDN);
        loadCompressionRules(arcdev.getCompressionRules(), deviceDN);
        loadQueryRetrieveViews(arcdev, deviceDN);
    }

    @Override
    protected void mergeChilds(Device prev, Device device, String deviceDN)
            throws NamingException, ConfigurationException {
        ArchiveDeviceExtension aa = prev
                .getDeviceExtension(ArchiveDeviceExtension.class);
        ArchiveDeviceExtension bb = device
                .getDeviceExtension(ArchiveDeviceExtension.class);
        if (aa == null || bb == null)
            return;

        mergeAttributeFilters(aa, bb, deviceDN);
        mergeStorageDescriptors(aa, bb, deviceDN);
        mergeCompressionRules(aa.getCompressionRules(), bb.getCompressionRules(), deviceDN);
        mergeQueryRetrieveViews(aa, bb, deviceDN);
    }

    @Override
    protected void storeTo(ApplicationEntity ae, Attributes attrs) {
        ArchiveAEExtension ext = ae.getAEExtension(ArchiveAEExtension.class);
        if (ext == null)
            return;

        attrs.get("objectclass").add("dcmArchiveNetworkAE");
        LdapUtils.storeNotNull(attrs, "dcmStorageID", ext.getStorageID());
        LdapUtils.storeNotNull(attrs, "dcmBulkDataSpoolDirectory", ext.getBulkDataSpoolDirectory());
        LdapUtils.storeNotNull(attrs, "dcmQueryRetrieveViewID", ext.getQueryRetrieveViewID());
        LdapUtils.storeNotNull(attrs, "dcmQueryMatchUnknown", ext.getQueryMatchUnknown());
        LdapUtils.storeNotNull(attrs, "dcmPersonNameComponentOrderInsensitiveMatching",
                ext.getPersonNameComponentOrderInsensitiveMatching());
    }

    @Override
    protected void loadFrom(ApplicationEntity ae, Attributes attrs) throws NamingException {
        if (!LdapUtils.hasObjectClass(attrs, "dcmArchiveNetworkAE"))
            return;

        ArchiveAEExtension ext = new ArchiveAEExtension();
        ae.addAEExtension(ext);
        ext.setStorageID(LdapUtils.stringValue(attrs.get("dcmStorageID"), null));
        ext.setBulkDataSpoolDirectory(LdapUtils.stringValue(attrs.get("dcmBulkDataSpoolDirectory"), null));
        ext.setQueryRetrieveViewID(LdapUtils.stringValue(attrs.get("dcmQueryRetrieveViewID"), null));
        ext.setQueryMatchUnknown(LdapUtils.booleanValue(attrs.get("dcmQueryMatchUnknown"), null));
        ext.setPersonNameComponentOrderInsensitiveMatching(
                LdapUtils.booleanValue(attrs.get("dcmPersonNameComponentOrderInsensitiveMatching"), null));
    }

    @Override
    protected void storeDiffs(ApplicationEntity prev, ApplicationEntity ae, List<ModificationItem> mods) {
        ArchiveAEExtension aa = prev.getAEExtension(ArchiveAEExtension.class);
        ArchiveAEExtension bb = ae.getAEExtension(ArchiveAEExtension.class);
        if (aa == null || bb == null)
            return;

        LdapUtils.storeDiff(mods, "dcmStorageID", aa.getStorageID(), bb.getStorageID());
        LdapUtils.storeDiff(mods, "dcmBulkDataSpoolDirectory",
                aa.getBulkDataSpoolDirectory(), bb.getBulkDataSpoolDirectory());
        LdapUtils.storeDiff(mods, "dcmQueryRetrieveViewID", aa.getQueryRetrieveViewID(), bb.getQueryRetrieveViewID());
        LdapUtils.storeDiff(mods, "dcmQueryMatchUnknown", aa.getQueryMatchUnknown(), bb.getQueryMatchUnknown());
        LdapUtils.storeDiff(mods, "dcmPersonNameComponentOrderInsensitiveMatching",
                aa.getPersonNameComponentOrderInsensitiveMatching(),
                bb.getPersonNameComponentOrderInsensitiveMatching());
    }

    @Override
    protected void storeChilds(String aeDN, ApplicationEntity ae) throws NamingException {
        ArchiveAEExtension aeExt = ae.getAEExtension(ArchiveAEExtension.class);
        if (aeExt == null)
            return;

        storeCompressionRules(aeExt.getCompressionRules(), aeDN);
    }

    @Override
    protected void loadChilds(ApplicationEntity ae, String aeDN) throws NamingException {
        ArchiveAEExtension aeExt = ae.getAEExtension(ArchiveAEExtension.class);
        if (aeExt == null)
            return;

        loadCompressionRules(aeExt.getCompressionRules(), aeDN);
    }

    @Override
    protected void mergeChilds(ApplicationEntity prev, ApplicationEntity ae, String aeDN) throws NamingException {
        ArchiveAEExtension aa = prev.getAEExtension(ArchiveAEExtension.class);
        ArchiveAEExtension bb = ae.getAEExtension(ArchiveAEExtension.class);
        if (aa == null || bb == null)
            return;

        mergeCompressionRules(aa.getCompressionRules(), bb.getCompressionRules(), aeDN);
    }

    private void storeAttributeFilter(String deviceDN, ArchiveDeviceExtension arcDev)
            throws NamingException {
        for (Entity entity : Entity.values()) {
            config.createSubcontext(
                    LdapUtils.dnOf("dcmEntity", entity.name(), deviceDN),
                    storeTo(arcDev.getAttributeFilter(entity), entity, new BasicAttributes(true)));
        }
    }

    private static Attributes storeTo(AttributeFilter filter, Entity entity,  BasicAttributes attrs) {
        attrs.put("objectclass", "dcmAttributeFilter");
        attrs.put("dcmEntity", entity.name());
        attrs.put(tagsAttr("dcmTag", filter.getSelection()));
        LdapUtils.storeNotNull(attrs, "dcmCustomAttribute1", filter.getCustomAttribute1());
        LdapUtils.storeNotNull(attrs, "dcmCustomAttribute2", filter.getCustomAttribute2());
        LdapUtils.storeNotNull(attrs, "dcmCustomAttribute3", filter.getCustomAttribute3());
        return attrs;
    }
    private static Attribute tagsAttr(String attrID, int[] tags) {
        Attribute attr = new BasicAttribute(attrID);
        for (int tag : tags)
            attr.add(TagUtils.toHexString(tag));
        return attr;
    }


    private void loadAttributeFilters(ArchiveDeviceExtension device, String deviceDN)
            throws NamingException {
        NamingEnumeration<SearchResult> ne = config.search(deviceDN, "(objectclass=dcmAttributeFilter)");
        try {
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                AttributeFilter filter = new AttributeFilter(tags(attrs.get("dcmTag")));
                filter.setCustomAttribute1(valueSelector(attrs.get("dcmCustomAttribute1")));
                filter.setCustomAttribute2(valueSelector(attrs.get("dcmCustomAttribute2")));
                filter.setCustomAttribute3(valueSelector(attrs.get("dcmCustomAttribute3")));
                device.setAttributeFilter(
                        Entity.valueOf(LdapUtils.stringValue(attrs.get("dcmEntity"), null)),
                        filter);
            }
        } finally {
            LdapUtils.safeClose(ne);
        }
    }

    private static ValueSelector valueSelector(Attribute attr)
            throws NamingException {
        return attr != null ? ValueSelector.valueOf((String) attr.get()) : null;
    }

    private static int[] tags(Attribute attr) throws NamingException {
        int[] is = new int[attr.size()];
        for (int i = 0; i < is.length; i++)
            is[i] = Integer.parseInt((String) attr.get(i), 16);

        return is;
    }

    private void mergeAttributeFilters(ArchiveDeviceExtension prev, ArchiveDeviceExtension devExt,
                                       String deviceDN) throws NamingException {
        for (Entity entity : Entity.values())
            config.modifyAttributes(
                    LdapUtils.dnOf("dcmEntity", entity.toString(), deviceDN),
                    storeDiffs(prev.getAttributeFilter(entity),
                            devExt.getAttributeFilter(entity),
                            new ArrayList<ModificationItem>()));
    }

    private List<ModificationItem> storeDiffs(AttributeFilter prev, AttributeFilter filter,
                                              List<ModificationItem> mods) {
        storeDiffTags(mods, "dcmTag", prev.getSelection(), filter.getSelection());
        LdapUtils.storeDiff(mods, "dcmCustomAttribute1",
                prev.getCustomAttribute1(), filter.getCustomAttribute1());
        LdapUtils.storeDiff(mods, "dcmCustomAttribute2",
                prev.getCustomAttribute2(), filter.getCustomAttribute2());
        LdapUtils.storeDiff(mods, "dcmCustomAttribute3",
                prev.getCustomAttribute3(), filter.getCustomAttribute3());
        return mods;
    }

    private void storeDiffTags(List<ModificationItem> mods, String attrId, int[] prevs, int[] vals) {
        if (!Arrays.equals(prevs, vals))
            mods.add(new ModificationItem(DirContext.REPLACE_ATTRIBUTE, tagsAttr(attrId, vals)));
    }

    private void storeStorageDescriptors(String deviceDN, ArchiveDeviceExtension arcDev) throws NamingException {
        for (StorageDescriptor descriptor : arcDev.getStorageDescriptors()) {
            String storageID = descriptor.getStorageID();
            config.createSubcontext(
                    LdapUtils.dnOf("dcmStorageID", storageID, deviceDN),
                    storeTo(descriptor, new BasicAttributes(true)));
        }
    }

    private Attributes storeTo(StorageDescriptor descriptor, BasicAttributes attrs) {
        attrs.put("objectclass", "dcmStorage");
        attrs.put("dcmStorageID", descriptor.getStorageID());
        attrs.put("dcmURI", descriptor.getStorageURI().toString());
        LdapUtils.storeNotNull(attrs, "dcmDigestAlgorithm", descriptor.getDigestAlgorithm());
        LdapUtils.storeNotNull(attrs, "dcmInstanceAvailability", descriptor.getInstanceAvailability());
        LdapUtils.storeNotEmpty(attrs, "dcmRetrieveAET", descriptor.getRetrieveAETitles());
        LdapUtils.storeNotEmpty(attrs, "dcmProperty", toStrings(descriptor.getProperties()));
        return attrs;
    }

    private String[] toStrings(Map<String, String> props) {
        String[] ss = new String[props.size()];
        int i = 0;
        for (Map.Entry<String,String> entry : props.entrySet())
            ss[i] = entry.getKey() + '=' + entry.getValue();
        return ss;
    }

    private void loadStorageDescriptors(ArchiveDeviceExtension arcdev, String deviceDN) throws NamingException {
        NamingEnumeration<SearchResult> ne = config.search(deviceDN, "(objectclass=dcmStorage)");
        try {
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                StorageDescriptor desc = new StorageDescriptor(LdapUtils.stringValue(attrs.get("dcmStorageID"), null));
                desc.setStorageURI(URI.create(LdapUtils.stringValue(attrs.get("dcmURI"), null)));
                desc.setDigestAlgorithm(LdapUtils.stringValue(attrs.get("dcmDigestAlgorithm"), null));
                desc.setInstanceAvailability(
                        LdapUtils.enumValue(Availability.class, attrs.get("dcmInstanceAvailability"), null));
                desc.setRetrieveAETitles(LdapUtils.stringArray(attrs.get("dcmRetrieveAET")));
                for (String s : LdapUtils.stringArray(attrs.get("dcmProperty"))) {
                    String[] ss = StringUtils.split(s, '=');
                    desc.setProperty(ss[0], ss[1]);
                }
                arcdev.addStorageDescriptor(desc);
            }
        } finally {
            LdapUtils.safeClose(ne);
        }
    }

    private void mergeStorageDescriptors(ArchiveDeviceExtension prev, ArchiveDeviceExtension arcDev, String deviceDN)
            throws NamingException {
        for (StorageDescriptor descriptor : prev.getStorageDescriptors()) {
            String storageID = descriptor.getStorageID();
            if (arcDev.getStorageDescriptor(storageID) == null)
                config.destroySubcontext(LdapUtils.dnOf("dcmStorageID", storageID, deviceDN));
        }
        for (StorageDescriptor descriptor : arcDev.getStorageDescriptors()) {
            String storageID = descriptor.getStorageID();
            String dn = LdapUtils.dnOf("dcmStorageID", storageID, deviceDN);
            StorageDescriptor prevDescriptor = prev.getStorageDescriptor(storageID);
            if (prevDescriptor == null)
                config.createSubcontext(dn,
                        storeTo(descriptor, new BasicAttributes(true)));
            else
                config.modifyAttributes(dn,
                        storeDiffs(prevDescriptor, descriptor, new ArrayList<ModificationItem>()));
        }
    }

    private List<ModificationItem> storeDiffs(StorageDescriptor prev, StorageDescriptor desc,
                                              List<ModificationItem> mods) {
        LdapUtils.storeDiff(mods, "dcmURI",
                prev.getStorageURI().toString(), desc.getStorageURI().toString());
        LdapUtils.storeDiff(mods, "dcmDigestAlgorithm",
                prev.getDigestAlgorithm(), desc.getDigestAlgorithm());
        LdapUtils.storeDiff(mods, "dcmInstanceAvailability",
                prev.getInstanceAvailability(), desc.getInstanceAvailability());
        LdapUtils.storeDiff(mods, "dcmRetrieveAET",
                prev.getRetrieveAETitles(), desc.getRetrieveAETitles());
        storeDiffProperties(mods, prev.getProperties(), desc.getProperties());
        return mods;
    }

    private void storeDiffProperties(List<ModificationItem> mods, Map<String, String> prev, Map<String, String> props) {
        if (!prev.equals(props)) {
            mods.add(props.size() == 0
                    ? new ModificationItem(DirContext.REMOVE_ATTRIBUTE,
                        new BasicAttribute("dcmProperty"))
                    : new ModificationItem(DirContext.REPLACE_ATTRIBUTE,
                        LdapUtils.attr("dcmProperty", toStrings(props))));
        }
    }

    private void storeCompressionRules(CompressionRules rules, String parentDN) throws NamingException {
        new LdapCompressionRulesConfiguration(config).store(rules, parentDN);
    }

    private void loadCompressionRules(CompressionRules rules, String parentDN) throws NamingException {
        rules.clear();
        new LdapCompressionRulesConfiguration(config).load(rules, parentDN);
    }

    private void mergeCompressionRules(CompressionRules aa, CompressionRules bb, String parentDN)
            throws NamingException {
        new LdapCompressionRulesConfiguration(config).merge(aa, bb, parentDN);
    }

    private void storeQueryRetrieveViews(String deviceDN, ArchiveDeviceExtension arcDev) throws NamingException {
        for (QueryRetrieveView view : arcDev.getQueryRetrieveViews())
            config.createSubcontext(
                    LdapUtils.dnOf("dcmQueryRetrieveViewID", view.getViewID(), deviceDN),
                    storeTo(view, new BasicAttributes(true)));
    }

    private Attributes storeTo(QueryRetrieveView qrView, BasicAttributes attrs) {
        attrs.put("objectclass", "dcmQueryRetrieveView");
        attrs.put("dcmQueryRetrieveViewID", qrView.getViewID());
        LdapUtils.storeNotEmpty(attrs, "dcmShowInstancesRejectedByCode", qrView.getShowInstancesRejectedByCodes());
        LdapUtils.storeNotEmpty(attrs, "dcmHideRejectionNoteWithCode", qrView.getHideRejectionNotesWithCodes());
        LdapUtils.storeNotDef(attrs, "dcmHideNotRejectedInstances", qrView.isHideNotRejectedInstances(), false);
        return attrs;
    }

    private void loadQueryRetrieveViews(ArchiveDeviceExtension arcdev, String deviceDN) throws NamingException {
        ArrayList<QueryRetrieveView> views = new ArrayList<>();
        NamingEnumeration<SearchResult> ne = config.search(deviceDN, "(objectclass=dcmQueryRetrieveView)");
        try {
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                QueryRetrieveView view = new QueryRetrieveView();
                view.setViewID(LdapUtils.stringValue(attrs.get("dcmQueryRetrieveViewID"), null));
                view.setShowInstancesRejectedByCodes(
                        LdapUtils.codeArray(attrs.get("dcmShowInstancesRejectedByCode")));
                view.setHideRejectionNotesWithCodes(
                        LdapUtils.codeArray(attrs.get("dcmHideRejectionNoteWithCode")));
                view.setHideNotRejectedInstances(
                        LdapUtils.booleanValue(attrs.get("dcmHideNotRejectedInstances"), false));
                views.add(view);
            }
        } finally {
            LdapUtils.safeClose(ne);
        }
        arcdev.setQueryRetrieveViews(views.toArray(new QueryRetrieveView[views.size()]));
    }

    private void mergeQueryRetrieveViews(ArchiveDeviceExtension prev, ArchiveDeviceExtension arcDev, String deviceDN)
            throws NamingException {
        for (QueryRetrieveView entry : prev.getQueryRetrieveViews()) {
            String viewID = entry.getViewID();
            if (arcDev.getQueryRetrieveView(viewID) == null)
                config.destroySubcontext( LdapUtils.dnOf("dcmQueryRetrieveViewID", viewID, deviceDN));
        }
        for (QueryRetrieveView entryNew : arcDev.getQueryRetrieveViews()) {
            String viewID = entryNew.getViewID();
            String dn = LdapUtils.dnOf("dcmQueryRetrieveViewID", viewID, deviceDN);
            QueryRetrieveView entryOld = prev.getQueryRetrieveView(viewID);
            if (entryOld == null) {
                config.createSubcontext(dn, storeTo(entryNew, new BasicAttributes(true)));
            } else{
                config.modifyAttributes(dn, storeDiffs(entryOld, entryNew, new ArrayList<ModificationItem>()));
            }
        }
    }

    private List<ModificationItem> storeDiffs(
            QueryRetrieveView prev, QueryRetrieveView view, ArrayList<ModificationItem> mods) {
        LdapUtils.storeDiff(mods, "dcmShowInstancesRejectedByCode",
                prev.getShowInstancesRejectedByCodes(),
                view.getShowInstancesRejectedByCodes());
        LdapUtils.storeDiff(mods, "dcmHideRejectionNoteWithCode",
                prev.getHideRejectionNotesWithCodes(),
                view.getHideRejectionNotesWithCodes());
        LdapUtils.storeDiff(mods, "dcmHideNotRejectedInstances",
                prev.isHideNotRejectedInstances(),
                view.isHideNotRejectedInstances(),
                false);
        return mods;
    }
}
