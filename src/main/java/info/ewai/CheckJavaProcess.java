package info.ewai;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class CheckJavaProcess {

    // [CMD_PS] | grep [FIND_PROCESS_KEYWORD]
    private final String CMD_PS = "ps aux";
    private final String FIND_PROCESS_KEYWORD = "jdk";

    // top -bHc -n 1 -p [find process]
    private final String CMD_TOP = "top -bHc -n 1 -p";
    // jstack [find java child process]
    private final String CMD_JSTACK = "jstack";

    /**
     * 1. Find java process
     * 2. Find java child process
     * 3. Get thread dump
     * 4. Link java process to thread dump
     * 5. Write target java process and thread dump
     * 
     * @param args 0:limit cpu, 1:limit time(s), 2:thread dump filter keyword
     * @throws InterruptedException
     * @throws IOException 
     */
    public static void main(String[] args) throws InterruptedException, IOException {
        CheckJavaProcess cjp = new CheckJavaProcess();
        int chkCpu = 50;
        int chkTime = 10;
        String keyword = "";
        if (args != null && args.length >= 2) {
            chkCpu = Integer.parseInt(args[0]);
            chkTime = Integer.parseInt(args[1]);
        }
        if (args != null && args.length >= 3) {
            keyword = args[2];
        }
        cjp.analyze(chkCpu, chkTime, keyword);
    }

    private void analyze(int chkCpu, int chkTime, String keyword) throws IOException, InterruptedException {

        // ps 
        String pid = getJavaProcess();
        System.out.println("pid=" + pid);

        // top -Hc -n 1 -p "java process"
        List<JavaProcess> javaDetailProcessList = getJavaDetailProcess(pid);
        javaDetailProcessList.forEach(System.out::println);

        // jstat "pid"
        List<String> threadDumpLineList = exec(CMD_JSTACK + " " + pid);

        // set thread dump
        System.out.println("---set thread dump");
        javaDetailProcessList.forEach(jp -> {
            jp.setThreadDumpListFromAllThreadDump(threadDumpLineList, keyword);
            // System.out.println(jp.toString());
        });
        System.out.println("---end set thread dump");

        // remove Less than limit
        System.out.println("before filter:" + javaDetailProcessList.size() + " keyword=" + keyword + " chkCpu=" + chkCpu + " chkTime=" + chkTime);
        javaDetailProcessList
                .removeIf(jp -> (jp.getCpu().compareTo(new BigDecimal(chkCpu)) < 0 || jp.getTime() < chkTime)
                        || !jp.isWrite(keyword));
        System.out.println("after filter:" + javaDetailProcessList.size());

        write(javaDetailProcessList, chkCpu, chkTime, keyword);
    }

    private String getJavaProcess() throws IOException {
        System.out.println("exec command[" + CMD_PS + " | grep " + FIND_PROCESS_KEYWORD + "]");
        String pid = "";

        Process p1 = Runtime.getRuntime().exec(CMD_PS);
        InputStream input = p1.getInputStream();

        Process p2 = Runtime.getRuntime().exec("grep " + FIND_PROCESS_KEYWORD);
        OutputStream output2 = p2.getOutputStream();

        copy(input, output2);
        output2.close();

        InputStreamReader isr = new InputStreamReader(p2.getInputStream());
        BufferedReader reader = new BufferedReader(isr);

        System.out.println("--- result success ---");
        for (String line; (line = reader.readLine()) != null;) {
            while (true) {
                if (line.indexOf("  ") == -1) break;
                line = line.replace("  ", " ");
            }
            pid = line.split(" ")[1];
            break;
        }
        return pid;
    }

    private List<JavaProcess> getJavaDetailProcess(String pid) throws IOException {
        List<JavaProcess> processList = new ArrayList<JavaProcess>();

        List<String> list = exec(CMD_TOP + " " + pid);
        boolean pflg = false;

        for (String line: list) {
            try {
                if (line.indexOf("PID") > -1) {
                    pflg = true;
                    continue;
                }
                if (pflg && line.length() > 0) {
                    processList.add(new JavaProcess(line));
                }
            } catch  (Exception e) {
                System.out.println("error line:[" + line + "]");
                e.printStackTrace();
            }
        }
        System.out.println("processList.size()=" + processList.size());

        return processList;
    }

    private void write(List<JavaProcess> JavaProcessList, int chkCpu, int chkTime, String keyword) throws IOException {
        List<String> writeList = new ArrayList<String>();
        JavaProcessList.forEach(jp -> {
            writeList.add("check limit[cpu:" + chkCpu + "(%), time:" + chkTime + "(s), keyword:" + keyword + "]");
            writeList.add("ps:["+ jp.getLine() + "]" + System.getProperty("line.separator"));
            writeList.add("pid:" + jp.getPid() + ", pid16:" + jp.getPid16() + ", cpu:" + jp.getCpu() + ", memory:" + jp.getMemory() + ", time:" + jp.getDisplayTime() + System.getProperty("line.separator"));
            writeList.add("target:" + jp.getTargetLine() + System.getProperty("line.separator"));
            writeList.add("thread dump ->");
            writeList.addAll(jp.getThreadDumpList());
            writeList.add("");
            writeList.add("########################################");
        });
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmm");
        String fileName = "check-java-process-" + sdf.format(cal.getTime()) + ".log";
        String dirName = "log";
        File dir = new File(dirName);
        dir.mkdirs();
        Files.write(new File(dir + "/" + fileName).toPath(), writeList, StandardCharsets.UTF_8);

        // target line only
        List<String> writeTargetList = new ArrayList<String>();
        JavaProcessList.stream().filter(jp -> jp.getTargetLine().length() > 0).forEach(jp -> {
            writeTargetList.add(jp.getDate() + " t[" + jp.getDisplayTime() + "] c[" + jp.getCpu() + "] m[" + jp.getMemory() + "] " + jp.getTargetLine());
        });
        sdf = new SimpleDateFormat("yyyyMMdd");
        fileName = "check-target-process-" + sdf.format(cal.getTime()) + ".log";
        Files.write(new File(dir + "/" + fileName).toPath(), writeTargetList, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private List<String> exec(String command) throws IOException {
        List<String> list = new ArrayList<String>();
        Process p = null;
        try {
            System.out.println("exec command[" + command + "]");
            p = Runtime.getRuntime().exec(command);
            p.waitFor();
            int exitValue = p.exitValue();
            System.out.println("exitValue=" + exitValue);
            if (exitValue != 0) {
                throw new RuntimeException("command error. status=" + exitValue);
            }
            System.out.println("success.");

            InputStreamReader isr = new InputStreamReader(p.getInputStream());
            BufferedReader reader = new BufferedReader(isr);
            System.out.println("--- result success ---");
            for (String line; (line = reader.readLine()) != null;) {
                list.add(line);
                System.out.println(line);
            }
            System.out.println("---");
        } catch (Exception e) {
            InputStreamReader isr = new InputStreamReader(p.getErrorStream());
            BufferedReader reader = new BufferedReader(isr);
            System.out.println("--- result error ---");
            for (String line; (line = reader.readLine()) != null;) {
                list.add(line);
                System.out.println(line);
            }
            System.out.println("---");
            throw new RuntimeException(e);
        } finally {
            p.destroy();
        }
        return list;
    }

    public static long copy(final InputStream input, final OutputStream output)
            throws IOException {
        final byte[] buffer = new byte[1024 * 4];
        long count = 0;
        int n;
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }
}
