package com.example.shortlink.health;


import java.net.InetAddress;
import java.net.URI;

/**
 * SSRF 安全策略。
 * 校验分两步进行，配合健康检测器的“解析一次、校验、固定地址直连”流程：
 * <ol>
 *   <li>{@link #validate(URI)}：在发起任何网络访问前，校验协议与 host 字符串；</li>
 *   <li>{@link #validateResolvedAddresses(InetAddress...)}：DNS 解析完成后，
 *   校验解析出的每一个地址，连接只能使用这些通过校验的地址。</li>
 * </ol>
 * 两步校验结合可以避免 DNS rebinding（TOCTOU）攻击：攻击者无法在
 * “校验时返回公网 IP、连接时返回内网 IP”，因为连接地址与校验地址是同一批。
 */
public interface AddressPolicy {

    /**
     * 校验 URI 的协议与 host 字符串，不通过时抛出 {@link AddressPolicyViolationException}。
     */
    void validate(URI uri);

    /**
     * 校验 DNS 解析返回的全部地址，不通过时抛出 {@link AddressPolicyViolationException}。
     * 调用方必须保证随后发起的连接只使用这里通过校验的地址。
     */
    void validateResolvedAddresses(InetAddress... addresses);
}
