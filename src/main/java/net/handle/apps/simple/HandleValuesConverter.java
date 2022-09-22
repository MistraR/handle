/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.simple;

import java.io.*;

import net.handle.hdllib.*;

public class HandleValuesConverter {

    public static String convertToJson(HandleValue[] values) {
        return GsonUtility.getNewGsonBuilder().disableHtmlEscaping().setPrettyPrinting().create().toJson(values);
    }

    public static HandleValue[] convertToHandleValues(String input) {
        return GsonUtility.getGson().fromJson(input, HandleValue[].class);
    }

    public static void convertToJson(byte[] input, OutputStream out) throws HandleException, IOException {
        HandleValue[] values = Encoder.decodeHandleValues(input);
        String json = convertToJson(values);
        out.write(json.getBytes("UTF-8"));
    }

    public static void convertToBin(String input, OutputStream out) throws IOException {
        HandleValue[] values = convertToHandleValues(input);
        out.write(Encoder.encodeGlobalValues(values));
    }

    public static void main(String[] args) {
        String outputFilename = null;
        String inputFilename = null;
        boolean sawDash = false;
        boolean expectingOutput = false;
        boolean sawEndOfOptions = false;
        for (String arg : args) {
            if (!sawEndOfOptions && arg.length() >= 2 && arg.startsWith("-")) {
                if (arg.equals("--")) sawEndOfOptions = true;
                else if (arg.equals("-o") || arg.equals("-output") || arg.equals("--output")) {
                    if (outputFilename != null) {
                        System.err.println("Too many output files specified");
                        System.exit(1);
                        return;
                    }
                    expectingOutput = true;
                } else if (arg.equals("-h") || arg.equals("-help") || arg.equals("--help")) {
                    System.err.println("arguments: [input-filename] [-o output-filename]");
                    System.err.println("input raw binary handle values (e.g. root_info) will be converted to json");
                    System.err.println("json input will be converted to raw binary handle values");
                    System.exit(0);
                    return;
                } else {
                    System.err.println("Unknown option " + arg);
                    System.exit(1);
                    return;
                }
            } else {
                if (expectingOutput) {
                    if (!arg.equals("-")) outputFilename = arg;
                } else {
                    if (sawDash || inputFilename != null) {
                        System.err.println("Too many input files specified");
                        System.exit(1);
                        return;
                    } else {
                        if (arg.equals("-")) sawDash = true;
                        else inputFilename = arg;
                    }
                }
            }
        }

        InputStream in = System.in;
        try {
            if (inputFilename != null) in = new FileInputStream(new File(inputFilename));
        } catch (FileNotFoundException e) {
            System.err.println("File " + inputFilename + " not found");
            System.exit(1);
            return;
        }
        OutputStream out;
        try {
            if (outputFilename != null) out = new FileOutputStream(new File(outputFilename));
            else out = System.out;
        } catch (FileNotFoundException e) {
            System.err.println("File " + outputFilename + " not writeable");
            System.exit(1);
            if (in != null) try { in.close(); } catch (Exception ignored) { }
            return;
        }

        byte[] buf = new byte[4096];
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        int r;
        try {
            while ((r = in.read(buf)) > 0) {
                bout.write(buf, 0, r);
            }
        } catch (IOException e) {
            System.err.println("IOException reading input");
            System.exit(1);
            return;
        } finally {
            try { in.close(); } catch (IOException e) { }
        }
        byte[] inputBytes = bout.toByteArray();

        try {
            if (Util.looksLikeBinary(inputBytes)) {
                convertToJson(inputBytes, out);
            } else {
                convertToBin(new String(inputBytes, "UTF-8"), out);
            }
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        } catch (Exception e) {
            System.err.println(e.getClass().getSimpleName() + ": " + e.getMessage());
            // e.printStackTrace();
            System.exit(1);
            return;
        } finally {
            try { out.close(); } catch (IOException e) { }
        }
    }
}
