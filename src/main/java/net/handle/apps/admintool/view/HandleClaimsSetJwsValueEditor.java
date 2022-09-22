/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.admintool.view;

import net.handle.awt.AwtUtil;
import net.handle.hdllib.*;
import net.handle.hdllib.trust.ChainBuilder;
import net.handle.hdllib.trust.ChainVerificationReport;
import net.handle.hdllib.trust.ChainVerifier;
import net.handle.hdllib.trust.DigestedHandleValues;
import net.handle.hdllib.trust.HandleClaimsSet;
import net.handle.hdllib.trust.HandleVerifier;
import net.handle.hdllib.trust.IssuedSignature;
import net.handle.hdllib.trust.JsonWebSignature;
import net.handle.hdllib.trust.JsonWebSignatureFactory;
import net.handle.hdllib.trust.TrustException;
import net.handle.hdllib.trust.ValuesSignatureVerificationReport;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.google.gson.Gson;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class HandleClaimsSetJwsValueEditor extends JPanel implements HandleValueEditor, ActionListener {
    @SuppressWarnings("hiding")
    private final AdminToolUI ui;
    private final JTextArea inputField;
    private final JTextArea signatureField;
    private final JButton signButton;
    private final JButton verifyButton;
    private final JLabel verifiedStatusLabel;
    private final JButton selectPublicKeyButton;
    private final boolean isHsCert;
    private final String handle;
    private final HandleValue[] values;
    private final EditValueWindow editValueWindow;

    public HandleClaimsSetJwsValueEditor(AdminToolUI ui, EditValueWindow window, boolean isHsCert, String handle, HandleValue[] values) {
        super();
        super.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        this.ui = ui;
        this.editValueWindow = window;
        this.isHsCert = isHsCert;
        this.handle = handle;
        this.values = values;

        inputField = new JTextArea(3, 3);
        signatureField = new JTextArea(1, 3);
        signatureField.setEditable(false);
        JScrollPane signatureScrollPane = new JScrollPane(signatureField);
        signatureScrollPane.setMaximumSize(new Dimension(1000, 45));
        inputField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void removeUpdate(DocumentEvent e) {
                setDoneEnabled(false);
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                setDoneEnabled(false);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                setDoneEnabled(false);
            }
        });

        signButton = new JButton();
        signButton.setText("Sign");
        signButton.addActionListener(this);

        verifyButton = new JButton();
        verifyButton.setText("Verify");
        verifyButton.addActionListener(this);

        verifiedStatusLabel = new JLabel("");
        verifiedStatusLabel.setVisible(false);

        selectPublicKeyButton = new JButton();
        selectPublicKeyButton.setText("Select Public Key To Be Signed");
        selectPublicKeyButton.addActionListener(this);

        add(leftAlignBox(new JLabel("Claims:", SwingConstants.LEFT)));
        add(new JScrollPane(inputField));
        if (isHsCert) {
            add(Box.createRigidArea(new Dimension(0, 5)));
            add(leftAlignBox(selectPublicKeyButton));
        }
        add(Box.createRigidArea(new Dimension(0, 5)));
        add(leftAlignBox(new JLabel("Signature:", SwingConstants.LEFT)));
        add(signatureScrollPane);
        add(Box.createRigidArea(new Dimension(0, 5)));

        Box signVerifyButtonBox = Box.createHorizontalBox();
        signVerifyButtonBox.add(signButton);
        signVerifyButtonBox.add(verifyButton);
        signVerifyButtonBox.add(Box.createHorizontalGlue());
        signVerifyButtonBox.add(verifiedStatusLabel);
        add(signVerifyButtonBox);
    }

    private Box leftAlignBox(JComponent thing) {
        Box box = Box.createHorizontalBox();
        box.add(thing);
        box.add(Box.createHorizontalGlue());
        return box;
    }

    @Override
    public boolean saveValueData(HandleValue value) {
        value.setData(Util.encodeString(signatureField.getText()));
        setDoneEnabled(true);
        return true;
    }

    private void resign() {
        try {
            verifiedStatusLabel.setText("");
            verifiedStatusLabel.setVisible(false);

            String prettyPayload = inputField.getText();
            Gson gson = GsonUtility.getPrettyGson();
            HandleClaimsSet claims = gson.fromJson(prettyPayload, HandleClaimsSet.class);
            SignerInfo sigInfo = ui.getSignatureInfo((Frame) this.editValueWindow.getOwner(), false);
            if (sigInfo == null) return;

            JsonWebSignature jws = sigInfo.signClaimsSet(claims);
            String jwsString = jws.serialize();
            signatureField.setText(jwsString);
            prettyPayload = gson.toJson(claims);
            inputField.setText(prettyPayload);
            setDoneEnabled(true);
        } catch (Exception e) {
            showError(e);
        }
    }

    @Override
    public void loadValueData(HandleValue value) {
        try {
            String signatureString = value.getDataAsString();
            HandleClaimsSet claims = null;
            Gson gson = GsonUtility.getPrettyGson();
            if (signatureString.isEmpty()) {
                long oneYearInSeconds = 366L * 24L * 60L * 60L;
                long expiration = System.currentTimeMillis() / 1000L + (oneYearInSeconds * 2);
                claims = new HandleClaimsSet();
                claims.sub = "";
                claims.iss = "";
                claims.iat = System.currentTimeMillis() / 1000L;
                claims.nbf = System.currentTimeMillis() / 1000L - 600;
                claims.exp = expiration;
                claims.chain = null; //Collections.emptyList();
                if (isHsCert) {
                    claims.perms = Collections.emptyList();
                } else {
                    claims.digests = new DigestedHandleValues();
                    claims.digests.alg = "SHA-256";
                    claims.digests.digests = Collections.emptyList();
                }
            } else {
                JsonWebSignature jws = JsonWebSignatureFactory.getInstance().deserialize(signatureString);
                String payload = jws.getPayloadAsString();
                claims = gson.fromJson(payload, HandleClaimsSet.class);
            }
            String prettyPayload = gson.toJson(claims);
            inputField.setText(prettyPayload);
            setDoneEnabled(true);
        } catch (Exception e) {
            showError(e);
        }

        signatureField.setText(value == null ? "" : Util.decodeString(value.getData()));
    }

    private void showError(Throwable t) {
        showError(t.toString());
        t.printStackTrace();
    }

    private void showError(String errorMessage) {
        JOptionPane.showMessageDialog(null, errorMessage, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void loadPublicKeyFromFile() {
        FileDialog fwin = new FileDialog(AwtUtil.getFrame(this), "Choose File to Load", FileDialog.LOAD);

        fwin.setVisible(true);

        String fileName = fwin.getFile();
        String dirName = fwin.getDirectory();
        if (fileName == null || dirName == null) return;

        try {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            byte buf[] = new byte[1024];
            FileInputStream fin = new FileInputStream(new File(dirName, fileName));
            try {
                int r;
                while ((r = fin.read(buf)) >= 0)
                    bout.write(buf, 0, r);
            } finally {
                fin.close();
            }
            buf = bout.toByteArray();

            PublicKey publicKey = Util.getPublicKeyFromBytes(buf);

            Gson gson = GsonUtility.getPrettyGson();

            String prettyPayload = inputField.getText();

            HandleClaimsSet claims = gson.fromJson(prettyPayload, HandleClaimsSet.class);
            claims.publicKey = publicKey;
            prettyPayload = gson.toJson(claims);
            inputField.setText(prettyPayload);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error: loading file: " + e);
        }
    }

    private List<PublicKey> getRootKeys() throws Exception {
        return ui.getMain().getResolver().getConfiguration().getRootKeys();
    }

    public void verify() {
        String signatureString = signatureField.getText();

        try {
            JsonWebSignature jws = JsonWebSignatureFactory.getInstance().deserialize(signatureString);
            String message = "";
            ChainBuilder chainBuilder = new ChainBuilder(ui.getMain().getResolver());
            List<IssuedSignature> issuedSignatures;
            try {
                issuedSignatures = chainBuilder.buildChain(jws);
            } catch (TrustException e) {
                if (!isHsCert) {
                    try {
                        HandleClaimsSet claims = HandleVerifier.getInstance().getHandleClaimsSet(jws);
                        String issuer = claims.iss;
                        ValueReference issuerValRef = ValueReference.fromString(issuer);
                        HandleValue handleValue = ui.getMain().getResolver().resolveValueReference(issuerValRef);
                        if (handleValue != null) {
                            PublicKey issuerPublicKey = Util.getPublicKeyFromBytes(handleValue.getData());
                            ValuesSignatureVerificationReport valuesReport = HandleVerifier.getInstance().verifyValues(handle, Util.filterOnlyPublicValues(Arrays.asList(values)), jws, issuerPublicKey);
                            String valuesReportJson = GsonUtility.getPrettyGson().toJson(valuesReport);
                            System.out.println(valuesReportJson);
                        }
                    } catch (Exception ex) {
                        // ignore
                    }
                }
                message = "Signature NOT VERIFIED unable to build chain: " + e.getMessage();
                System.out.println(message);
                e.printStackTrace(System.out);
                verifiedStatusLabel.setText(message);
                verifiedStatusLabel.setVisible(true);
                return;
            }
            ChainVerifier chainVerifier = new ChainVerifier(getRootKeys());

            if (isHsCert) {
                ChainVerificationReport chainReport = chainVerifier.verifyChain(issuedSignatures);
                String chainReportJson = GsonUtility.getPrettyGson().toJson(chainReport);
                System.out.println(chainReportJson);
                if (chainReport.canTrust()) {
                    message = "Signature VERIFIED";
                    String publicKeyIssue = checkPublicKeyIssue(jws);
                    if (publicKeyIssue != null) {
                        message += "; WARNING " + publicKeyIssue;
                    }
                } else {
                    message = "Signature NOT VERIFIED";
                }
            } else {
                ChainVerificationReport report = chainVerifier.verifyValues(handle, Arrays.asList(values), issuedSignatures);
                String reportJson = GsonUtility.getPrettyGson().toJson(report);
                System.out.println(reportJson);
                boolean badDigests = report.valuesReport.badDigestValues.size() != 0;
                boolean missingValues = report.valuesReport.missingValues.size() != 0;
                if (report.canTrustAndAuthorized() && !badDigests && !missingValues) {
                    message = "Signature VERIFIED";
                } else {
                    message = "Signature NOT VERIFIED";
                    if (badDigests) {
                        message += " bad digests";
                    }
                    if (missingValues) {
                        message += " missing values";
                    }
                }
            }
            System.out.println(message);
            verifiedStatusLabel.setText(message);
            verifiedStatusLabel.setVisible(true);
        } catch (Exception e) {
            showError(e);
            e.printStackTrace();
        }
    }

    private String checkPublicKeyIssue(JsonWebSignature jws) {
        try {
            HandleClaimsSet claims = HandleVerifier.getInstance().getHandleClaimsSet(jws);
            PublicKey pubKeyInCert = claims.publicKey;
            byte[] certPubKeyBytes = Util.getBytesFromPublicKey(pubKeyInCert);
            ValueReference valRef = ValueReference.fromString(claims.sub);
            @SuppressWarnings("hiding")
            HandleValue[] values;
            if (valRef.index == 0) {
                values = ui.getMain().getResolver().resolveHandle(valRef.getHandleAsString(), new String[] { "HS_PUBKEY" }, null);
            } else {
                values = new HandleValue[] { ui.getMain().getResolver().resolveValueReference(valRef) };
            }
            for (HandleValue value : values) {
                if (Util.equals(certPubKeyBytes, value.getData())) {
                    return null;
                }
            }
            return "publicKey does not match subject";
        } catch (Exception e) {
            e.printStackTrace();
            return "exception checking publicKey: " + e.getMessage();
        }
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
        Object src = evt.getSource();
        if (src == signButton) {
            resign();
        } else if (src == selectPublicKeyButton) {
            loadPublicKeyFromFile();
        } else if (src == verifyButton) {
            verify();
        }
    }

    private void setDoneEnabled(boolean enabled) {
        editValueWindow.setDoneEnabled(enabled);
        if (enabled) {
            verifiedStatusLabel.setText("");
            verifiedStatusLabel.setVisible(false);
        } else {
            verifiedStatusLabel.setText("Must sign before done");
            verifiedStatusLabel.setVisible(true);
        }
    }

}
