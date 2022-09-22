/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server.bdbje;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.EnvironmentFailureException;

public class JeUpgradeTool {
    public static Environment openEnvironment(File envHome, EnvironmentConfig configuration) {
        try {
            return new Environment(envHome, configuration);
        } catch (EnvironmentFailureException e) {
            if (e.getMessage() != null && e.getMessage().contains("DbPreUpgrade_4_1")) {
                try {
                    upgrade(envHome);
                } catch (Exception ex) {
                    System.out.println("Storage JE version upgrade failed!: " + ex);
                    e.addSuppressed(ex);
                    throw e;
                }
                return new Environment(envHome, configuration);
            } else {
                throw e;
            }
        }
    }

    private static void upgrade(File envHome) throws Exception {
        System.out.println("Storage (" + envHome + ") requires JE version upgrade.  Performing now.");
        File file = new File(JeUpgradeTool.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        while (!"lib".equals(file.getName())) {
            file = file.getParentFile();
        }
        URL jeJar = new File(file.getParentFile(), "jeUpgradeTool/je-4.1.27.jar").toURI().toURL();
        URL jtaJar = new File(file.getParentFile(), "jeUpgradeTool/jta-1.1.jar").toURI().toURL();
        try (URLClassLoader classLoader = new URLClassLoader(new URL[] { jeJar, jtaJar }, null)) {
            Class<?> klass = classLoader.loadClass("com.sleepycat.je.util.DbPreUpgrade_4_1");
            Constructor<?> constructor = klass.getConstructor(File.class);
            Object upgrader = constructor.newInstance(envHome);
            Method method = klass.getMethod("preUpgrade");
            method.invoke(upgrader);
            System.out.println("Storage JE version upgrade succeeded.");
        }
    }
}
