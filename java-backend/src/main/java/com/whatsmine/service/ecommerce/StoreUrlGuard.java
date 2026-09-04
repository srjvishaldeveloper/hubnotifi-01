package com.whatsmine.service.ecommerce;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URL;

@Component
public class StoreUrlGuard {

    public static String validate(String platform, String domain) {
        if ("shopify".equalsIgnoreCase(platform)) {
            return validateShopify(domain);
        } else if ("bigcommerce".equalsIgnoreCase(platform)) {
            return validateBigCommerce(domain);
        } else if ("woocommerce".equalsIgnoreCase(platform)) {
            return validateWoo(domain);
        } else {
            return "Unsupported platform.";
        }
    }

    private static String validateShopify(String domain) {
        if (domain != null && domain.matches("(?i)^[a-z0-9][a-z0-9-]*\\.myshopify\\.com$")) {
            return null;
        }
        return "Shopify domain must be your store's *.myshopify.com address.";
    }

    private static String validateBigCommerce(String hash) {
        if (hash != null && hash.matches("(?i)^[a-z0-9]+$")) {
            return null;
        }
        return "BigCommerce store hash is invalid (expected the alphanumeric code from your API path).";
    }

    private static String validateWoo(String urlStr) {
        if (urlStr == null || !urlStr.matches("(?i)^https?://.*")) {
            return "Store URL must start with http:// or https://.";
        }

        try {
            URL url = new URL(urlStr);
            String host = url.getHost();
            if (host == null || host.trim().isEmpty()) {
                return "Store URL is not a valid URL.";
            }

            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses == null || addresses.length == 0) {
                return "Store URL host could not be resolved.";
            }

            for (InetAddress addr : addresses) {
                if (addr.isAnyLocalAddress() || addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress()) {
                    return "Store URL must point to a public host (private/internal addresses are blocked).";
                }
            }
        } catch (Exception e) {
            return "Store URL host could not be resolved.";
        }

        return null;
    }
}
