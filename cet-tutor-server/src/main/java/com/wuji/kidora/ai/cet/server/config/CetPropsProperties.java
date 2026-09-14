package com.wuji.kidora.ai.cet.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CET 本地教具目录配置。
 *
 * @author liudy
 */
@ConfigurationProperties(prefix = "kidora.cet.props")
public class CetPropsProperties {

    /**
     * 教具文件根目录（绝对或相对工作目录）。storage_path 相对此目录。
     */
    private String localDir = "./data/cet-props";

    public String getLocalDir() {
        return localDir;
    }

    public void setLocalDir(String localDir) {
        this.localDir = localDir;
    }
}
