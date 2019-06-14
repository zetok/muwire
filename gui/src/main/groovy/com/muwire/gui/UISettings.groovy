package com.muwire.gui

class UISettings {
    
    String lnf
    boolean showMonitor
    String font
    boolean clearCancelledDownloads
    boolean clearFinishedDownloads
    boolean excludeLocalResult
    
    UISettings(Properties props) {
        lnf = props.getProperty("lnf", "system")
        showMonitor = Boolean.parseBoolean(props.getProperty("showMonitor", "true"))
        font = props.getProperty("font",null)
        clearCancelledDownloads = Boolean.parseBoolean(props.getProperty("clearCancelledDownloads","false"))
        clearFinishedDownloads = Boolean.parseBoolean(props.getProperty("clearFinishedDownloads","false"))
        excludeLocalResult = Boolean.parseBoolean(props.getProperty("excludeLocalResult","false"))
    }
    
    void write(OutputStream out) throws IOException {
        Properties props = new Properties()
        props.setProperty("lnf", lnf)
        props.setProperty("showMonitor", String.valueOf(showMonitor))
        props.setProperty("clearCancelledDownloads", String.valueOf(clearCancelledDownloads))
        props.setProperty("clearFinishedDownloads", String.valueOf(clearFinishedDownloads))
        props.setProperty("excludeLocalResult", String.valueOf(excludeLocalResult))
        if (font != null)
            props.setProperty("font", font)
            
            
        props.store(out, "UI Properties")
    }
}
