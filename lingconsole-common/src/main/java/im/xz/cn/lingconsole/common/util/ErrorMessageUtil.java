/*
 * LingConsole - A Server WebUI control panel
 * Copyright (C) 2026  XIAZHIRUI HUANG
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package im.xz.cn.lingconsole.common.util;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;


public final class ErrorMessageUtil {

    
    public static final String GENERIC = "服务器内部错误, 请查看服务端日志";

    private ErrorMessageUtil() {
    }

    
    public static String friendly(Throwable e) {
        if (e == null) {
            return GENERIC;
        }
        Throwable cause = e;
        Throwable next;
        while ((next = cause.getCause()) != null && next != cause) {
            cause = next;
        }
        String msg = cause.getMessage();
        boolean hasMsg = msg != null && !msg.isBlank() && !"null".equalsIgnoreCase(msg.trim());
        if (!hasMsg) {
            return genericFor(cause);
        }
        return maskPaths(specificFor(cause, msg));
    }

    
    public static String with(String action, Throwable e) {
        return action + ": " + friendly(e);
    }

    
    public static String maskPaths(String msg) {
        if (msg == null) {
            return null;
        }
        
        String masked = msg.replaceAll(
                "[a-zA-Z]:\\\\[^\\s\"'()\\n]*(?:\\s[^\\s\"'()\\n]+)*", "<路径>");
        
        masked = masked.replaceAll("(?<=[: ])/(?:[A-Za-z0-9._~-]+/)+[A-Za-z0-9._~-]*", "<路径>");
        return masked;
    }

    private static String genericFor(Throwable cause) {
        if (cause instanceof NullPointerException) {
            return "服务器内部错误 (空指针异常)";
        }
        if (cause instanceof TimeoutException
                || cause instanceof SocketTimeoutException
                || cause instanceof HttpTimeoutException) {
            return "操作超时, 请稍后重试";
        }
        if (cause instanceof ConnectException) {
            return "无法连接到目标节点 (连接被拒绝)";
        }
        return GENERIC;
    }

    private static String specificFor(Throwable cause, String msg) {
        if (cause instanceof NullPointerException) {
            return "服务器内部错误";
        }
        if (cause instanceof ConnectException) {
            return "无法连接到目标节点 (连接被拒绝): " + msg;
        }
        if (cause instanceof TimeoutException
                || cause instanceof SocketTimeoutException
                || cause instanceof HttpTimeoutException) {
            return "连接超时, 请检查目标节点是否可达";
        }
        if (cause instanceof com.fasterxml.jackson.core.JsonProcessingException) {
            return "请求数据格式错误";
        }
        return msg;
    }
}
