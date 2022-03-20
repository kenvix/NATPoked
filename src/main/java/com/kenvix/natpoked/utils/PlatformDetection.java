//--------------------------------------------------
// Class PlatformDetection
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.utils;

import org.apache.commons.lang3.SystemUtils;

import java.util.HashMap;
import java.util.Map;

public class PlatformDetection {
    private String os;
    private String arch;
    public static String OS_WINDOWS = "windows";
    public static String OS_OSX = "osx";
    public static String OS_SOLARIS = "solaris";
    public static String OS_LINUX = "linux";
    public static String ARCH_PPC = "ppc";
    public static String ARCH_X86_32 = "x86_32";
    public static String ARCH_X86_64 = "x86_64";
    public static String ARCH_AARCH_64 = "aarch64";

    public PlatformDetection() {
        // resolve OS
        if (SystemUtils.IS_OS_WINDOWS) {
            this.os = OS_WINDOWS;
        } else if (SystemUtils.IS_OS_MAC_OSX) {
            this.os = OS_OSX;
        } else if (SystemUtils.IS_OS_SOLARIS) {
            this.os = OS_SOLARIS;
        } else if (SystemUtils.IS_OS_LINUX) {
            this.os = OS_LINUX;
        } else {
            throw new IllegalArgumentException("Unknown operating system " + SystemUtils.OS_NAME);
        }

        // resolve architecture
        Map<String, String> archMap = new HashMap<String, String>();
        archMap.put("x86", ARCH_X86_32);
        archMap.put("i386", ARCH_X86_32);
        archMap.put("i486", ARCH_X86_32);
        archMap.put("i586", ARCH_X86_32);
        archMap.put("i686", ARCH_X86_32);
        archMap.put("x86_64", ARCH_X86_64);
        archMap.put("amd64", ARCH_X86_64);
        archMap.put("powerpc", ARCH_PPC);
        archMap.put("aarch64", ARCH_AARCH_64);
        this.arch = archMap.get(SystemUtils.OS_ARCH);
        if (this.arch == null) {
            throw new IllegalArgumentException("Unknown architecture " + SystemUtils.OS_ARCH);
        }
    }

    public String getOs() {
        return os;
    }

    public String getArch() {
        return arch;
    }

    public void setArch(String arch) {
        this.arch = arch;
    }

    public void setOs(String os) {
        this.os = os;
    }

    public String toString() {

        return os + "_" + arch;
    }
}