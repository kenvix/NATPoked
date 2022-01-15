//--------------------------------------------------
// Class JvmInfo
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.web.utils;

import java.io.PrintStream;
import java.lang.management.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public class JvmInfo {
    static final long MB = 1024 * 1024;

    public static void print(PrintStream printer) {
        //打印系统信息
        printer.println("===========打印系统信息==========");
        printOperatingSystemInfo(printer);
        //打印编译信息
        printer.println("===========打印编译信息==========");
        printCompilationInfo(printer);
        //打印类加载信息
        printer.println("===========打印类加载信息==========");
        printClassLoadingInfo(printer);
        //打印运行时信息
        printer.println("===========打印运行时信息==========");
        printRuntimeInfo(printer);
        //打印内存管理器信息
        printer.println("===========打印内存管理器信息==========");
        printMemoryManagerInfo(printer);
        //打印垃圾回收信息
        printer.println("===========打印垃圾回收信息==========");
        printGarbageCollectorInfo(printer);
        //打印vm内存
        printer.println("===========打印vm内存信息==========");
        printMemoryInfo(printer);
        //打印vm各内存区信息
        printer.println("===========打印vm各内存区信息==========");
        printMemoryPoolInfo(printer);
        //打印线程信息
        printer.println("===========打印线程==========");
        printThreadInfo(printer);

    }

    private static void printOperatingSystemInfo(PrintStream printer) {
        OperatingSystemMXBean system = ManagementFactory.getOperatingSystemMXBean();
        //相当于System.getProperty("os.name").
        printer.println("系统名称:" + system.getName());
        //相当于System.getProperty("os.version").
        printer.println("系统版本:" + system.getVersion());
        //相当于System.getProperty("os.arch").
        printer.println("操作系统的架构:" + system.getArch());
        //相当于 Runtime.availableProcessors()
        printer.println("可用的内核数:" + system.getAvailableProcessors());

        if (isSunOsMBean(system)) {
            long totalPhysicalMemory = getLongFromOperatingSystem(system, "getTotalPhysicalMemorySize");
            long freePhysicalMemory = getLongFromOperatingSystem(system, "getFreePhysicalMemorySize");
            long usedPhysicalMemorySize = totalPhysicalMemory - freePhysicalMemory;

            printer.println("总物理内存(M):" + totalPhysicalMemory / MB);
            printer.println("已用物理内存(M):" + usedPhysicalMemorySize / MB);
            printer.println("剩余物理内存(M):" + freePhysicalMemory / MB);

            long totalSwapSpaceSize = getLongFromOperatingSystem(system, "getTotalSwapSpaceSize");
            long freeSwapSpaceSize = getLongFromOperatingSystem(system, "getFreeSwapSpaceSize");
            long usedSwapSpaceSize = totalSwapSpaceSize - freeSwapSpaceSize;

            printer.println("总交换空间(M):" + totalSwapSpaceSize / MB);
            printer.println("已用交换空间(M):" + usedSwapSpaceSize / MB);
            printer.println("剩余交换空间(M):" + freeSwapSpaceSize / MB);
        }
    }

    private static long getLongFromOperatingSystem(OperatingSystemMXBean operatingSystem, String methodName) {
        try {
            final Method method = operatingSystem.getClass().getMethod(methodName,
                    (Class<?>[]) null);
            method.setAccessible(true);
            return (Long) method.invoke(operatingSystem, (Object[]) null);
        } catch (final InvocationTargetException e) {
            if (e.getCause() instanceof Error) {
                throw (Error) e.getCause();
            } else if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw new IllegalStateException(e.getCause());
        } catch (final NoSuchMethodException e) {
            throw new IllegalArgumentException(e);
        } catch (final IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void printCompilationInfo(PrintStream printer) {
        CompilationMXBean compilation = ManagementFactory.getCompilationMXBean();
        printer.println("JIT编译器名称：" + compilation.getName());
        //判断jvm是否支持编译时间的监控
        if (compilation.isCompilationTimeMonitoringSupported()) {
            printer.println("总编译时间：" + compilation.getTotalCompilationTime() + "秒");
        }
    }

    private static void printClassLoadingInfo(PrintStream printer) {
        ClassLoadingMXBean classLoad = ManagementFactory.getClassLoadingMXBean();
        printer.println("已加载类总数：" + classLoad.getTotalLoadedClassCount());
        printer.println("已加载当前类：" + classLoad.getLoadedClassCount());
        printer.println("已卸载类总数：" + classLoad.getUnloadedClassCount());

    }

    private static void printRuntimeInfo(PrintStream printer) {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        printer.println("进程PID=" + runtime.getName().split("@")[0]);
        printer.println("jvm规范名称:" + runtime.getSpecName());
        printer.println("jvm规范运营商:" + runtime.getSpecVendor());
        printer.println("jvm规范版本:" + runtime.getSpecVersion());
        //返回虚拟机在毫秒内的开始时间。该方法返回了虚拟机启动时的近似时间
        printer.println("jvm启动时间（毫秒）:" + runtime.getStartTime());
        //相当于System.getProperties
        printer.println("jvm正常运行时间（毫秒）:" + runtime.getUptime());
        //相当于System.getProperty("java.vm.name").
        printer.println("jvm名称:" + runtime.getVmName());
        //相当于System.getProperty("java.vm.vendor").
        printer.println("jvm运营商:" + runtime.getVmVendor());
        //相当于System.getProperty("java.vm.version").
        printer.println("jvm实现版本:" + runtime.getVmVersion());
        List<String> args = runtime.getInputArguments();
        if (args != null && !args.isEmpty()) {
            printer.println("vm参数:");
            for (String arg : args) {
                printer.println(arg);
            }
        }
//        printer.println("类路径:" + runtime.getClassPath());
//        printer.println("引导类路径:" + runtime.getBootClassPath());
//        printer.println("库路径:" + runtime.getLibraryPath());
    }

    private static void printMemoryManagerInfo(PrintStream printer) {
        List<MemoryManagerMXBean> managers = ManagementFactory.getMemoryManagerMXBeans();
        if (managers != null && !managers.isEmpty()) {
            for (MemoryManagerMXBean manager : managers) {
                printer.println("vm内存管理器：名称=" + manager.getName() + ",管理的内存区="
                        + Arrays.deepToString(manager.getMemoryPoolNames()) + ",ObjectName=" + manager.getObjectName());
            }
        }
    }

    private static void printGarbageCollectorInfo(PrintStream printer) {
        List<GarbageCollectorMXBean> garbages = ManagementFactory.getGarbageCollectorMXBeans();
        for (GarbageCollectorMXBean garbage : garbages) {
            printer.println("垃圾收集器：名称=" + garbage.getName() + ",收集=" + garbage.getCollectionCount() + ",总花费时间="
                    + garbage.getCollectionTime() + ",内存区名称=" + Arrays.deepToString(garbage.getMemoryPoolNames()));
        }
    }

    private static void printMemoryInfo(PrintStream printer) {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        MemoryUsage headMemory = memory.getHeapMemoryUsage();
        printer.println("head堆:");
        printer.println("初始(M):" + headMemory.getInit() / MB);
        printer.println("最大(上限)(M):" + headMemory.getMax() / MB);
        printer.println("当前(已使用)(M):" + headMemory.getUsed() / MB);
        printer.println("提交的内存(已申请)(M):" + headMemory.getCommitted() / MB);
        printer.println("使用率:" + headMemory.getUsed() * 100 / headMemory.getCommitted() + "%");

        printer.println("non-head非堆:");
        MemoryUsage nonheadMemory = memory.getNonHeapMemoryUsage();
        printer.println("初始(M):" + nonheadMemory.getInit() / MB);
        printer.println("最大(上限)(M):" + nonheadMemory.getMax() / MB);
        printer.println("当前(已使用)(M):" + nonheadMemory.getUsed() / MB);
        printer.println("提交的内存(已申请)(M):" + nonheadMemory.getCommitted() / MB);
        printer.println("使用率:" + nonheadMemory.getUsed() * 100 / nonheadMemory.getCommitted() + "%");
    }

    private static void printMemoryPoolInfo(PrintStream printer) {

    }

    private static void printThreadInfo(PrintStream printer) {
        ThreadMXBean thread = ManagementFactory.getThreadMXBean();
        printer.println("ObjectName=" + thread.getObjectName());
        printer.println("仍活动的线程总数=" + thread.getThreadCount());
        printer.println("峰值=" + thread.getPeakThreadCount());
        printer.println("线程总数（被创建并执行过的线程总数）=" + thread.getTotalStartedThreadCount());
        printer.println("当初仍活动的守护线程（daemonThread）总数=" + thread.getDaemonThreadCount());

        //检查是否有死锁的线程存在
        long[] deadlockedIds = thread.findDeadlockedThreads();
        if (deadlockedIds != null && deadlockedIds.length > 0) {
            ThreadInfo[] deadlockInfos = thread.getThreadInfo(deadlockedIds);
            printer.println("死锁线程信息:");
            printer.println(String.format("%80s %20s %10s %10s %50s", "线程名称", "状态", "BlockedTime", "WaitedTime", "StackTrace"));
            for (ThreadInfo deadlockInfo : deadlockInfos) {
                printer.println(String.format("%80s %20s %10s %10s %50s",deadlockInfo.getThreadName(), deadlockInfo.getThreadState(), deadlockInfo.getBlockedTime(),
                        deadlockInfo.getWaitedTime(), deadlockInfo.getStackTrace().toString()));
            }
        }
        long[] threadIds = thread.getAllThreadIds();
        if (threadIds != null && threadIds.length > 0) {
            ThreadInfo[] threadInfos = thread.getThreadInfo(threadIds);
            printer.println("所有线程信息:");
            printer.println(String.format("%80s %20s %10s", "线程名称", "状态", "ID"));
            for (ThreadInfo threadInfo : threadInfos) {
                printer.println(String.format("%80s %20s %10s",threadInfo.getThreadName(), threadInfo.getThreadState(), threadInfo.getThreadId()));
            }
        }

    }

    private static boolean isSunOsMBean(OperatingSystemMXBean operatingSystem) {
        final String className = operatingSystem.getClass().getName();
        return "com.sun.management.OperatingSystem".equals(className)
                || "com.sun.management.UnixOperatingSystem".equals(className)
                || "sun.management.OperatingSystemImpl".equals(className);
    }
}