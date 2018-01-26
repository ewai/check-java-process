package info.ewai;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class JavaProcess {

    public JavaProcess(String line) {
        while (true) {
            if (line.indexOf("  ") == -1) break;
            line = line.replace("  ", " ");
        }
        if (!line.startsWith(" ")) line = " " + line;
        String[] cols = line.split(" ");

        this.pid = cols[1];
        this.pid16 = Integer.toHexString(Integer.parseInt(pid));
        this.cpu = new BigDecimal(cols[9]);
        this.memory = cols[10];
        this.command = cols[12];
        this.displayTime = cols[11];
        String[] tmp = cols[11].substring(0, cols[11].indexOf(".")).split(":");
        this.time = Integer.parseInt(tmp[0]);
        this.time += Integer.parseInt(tmp[1]) * 60;
        this.line = line;
    }

    private String pid;
    private String pid16;
    private BigDecimal cpu;
    private String memory;
    private int time;
    private String displayTime;
    private String command;
    private String line;
    private boolean writeTarget = true;
    private String targetLine = "";
    private List<String> threadDumpList = new ArrayList<String>();

    public String getPid() {
        return pid;
    }
    public void setPid(String pid) {
        this.pid = pid;
    }
    public String getPid16() {
        return pid16;
    }
    public void setPid16(String pid16) {
        this.pid16 = pid16;
    }
    public BigDecimal getCpu() {
        return cpu;
    }
    public void setCpu(BigDecimal cpu) {
        this.cpu = cpu;
    }
    public String getMemory() {
        return memory;
    }
    public void setMemory(String memory) {
        this.memory = memory;
    }
    public int getTime() {
        return time;
    }
    public void setTime(int time) {
        this.time = time;
    }
    public String getDisplayTime() {
        return displayTime;
    }
    public void setDisplayTime(String displayTime) {
        this.displayTime = displayTime;
    }
    public String getCommand() {
        return command;
    }
    public void setCommand(String command) {
        this.command = command;
    }
    public String getLine() {
        return line;
    }
    public void setLine(String line) {
        this.line = line;
    }
    public boolean isWriteTarget() {
        return writeTarget;
    }
    public void setWriteTarget(boolean writeTarget) {
        this.writeTarget = writeTarget;
    }
    public String getTargetLine() {
        return targetLine;
    }
    public void setTargetLine(String targetLine) {
        this.targetLine = targetLine;
    }
    public List<String> getThreadDumpList() {
        return threadDumpList;
    }
    public void setThreadDumpList(List<String> threadDumpList) {
        this.threadDumpList = threadDumpList;
    }

    public String toString() {
        return pid + ", " + pid16 + ", " + cpu + ", " + time + ", " + command;
    }

    public void setThreadDumpListFromAllThreadDump(List<String> allThreadDumpList, String keyword) {
        threadDumpList.clear();
        boolean startFlg = false;
        for (String line : allThreadDumpList) {
            int startIndex = line.indexOf("nid=0x");
            if (startIndex > -1) {
                String linePid = line.substring(startIndex + 6, line.indexOf(" ", startIndex + 6));
                if (linePid.equals(pid16)) {
                    startFlg = true;
                    threadDumpList.add(line);
                    continue;
                } else {
                    startFlg = false;
                }
            }
            if (startFlg) {
                threadDumpList.add(line);
                if (targetLine.length() == 0 && line.indexOf(keyword) > -1) {
                    targetLine = line;
                }
            }
        }
    }

    public boolean isWrite(String keyword) {
        if (keyword == null || keyword.length() == 0) return true;
        for (String tmp : threadDumpList) {
            if (tmp.indexOf(keyword) > -1) {
                return true;
            }
        }
        return false;
    }
    
    public String getDate() {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        return sdf.format(cal.getTime());
    }
}
