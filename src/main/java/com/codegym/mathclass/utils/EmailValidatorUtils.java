package com.codegym.mathclass.utils;

import lombok.extern.slf4j.Slf4j;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;

@Slf4j
public class EmailValidatorUtils {

    /**
     * Kiểm tra tên miền (Domain) của Email có bản ghi DNS MX (Mail Exchange) nhận thư hợp lệ từ DNS công khai hay không.
     *
     * @param email Địa chỉ email cần kiểm tra
     * @return true nếu tên miền có máy chủ nhận thư MX hợp lệ, false nếu tên miền rác/ảo/không có MX record
     */
    public static boolean hasValidMxRecord(String email) {
        if (email == null || !email.contains("@")) {
            return false;
        }

        String domain = email.substring(email.indexOf("@") + 1).trim().toLowerCase();
        if (domain.isEmpty() || !domain.contains(".")) {
            return false;
        }

        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            // Trực tiếp chỉ định DNS Server công khai (Google DNS & Cloudflare DNS)
            // Tránh việc router/ISP DNS giải mã tất cả tên miền không tồn tại thành IP quảng cáo (DNS Catch-All / Hijacking)
            env.put("java.naming.provider.url", "dns://8.8.8.8 dns://1.1.1.1");
            env.put("com.sun.jndi.dns.timeout.initial", "2500");
            env.put("com.sun.jndi.dns.timeout.retries", "1");

            DirContext ictx = new InitialDirContext(env);
            
            // Tra cứu bản ghi MX (Mail Exchange) duy nhất - Chỉ các tên miền thực sự có máy chủ mail mới sở hữu bản ghi này
            Attributes attrs = ictx.getAttributes(domain, new String[]{"MX"});
            Attribute attr = attrs.get("MX");

            return attr != null && attr.size() > 0;
        } catch (NamingException e) {
            log.warn("Tra cứu DNS MX thất bại cho tên miền '{}': {}", domain, e.getMessage());
            return false;
        }
    }
}
