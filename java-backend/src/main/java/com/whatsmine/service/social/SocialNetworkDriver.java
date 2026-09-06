package com.whatsmine.service.social;

import com.whatsmine.model.SocialAccount;
import com.whatsmine.model.SocialPost;

import java.util.Map;

/** Porting PHP's Social\Services\Drivers\SocialNetworkInterface. */
public interface SocialNetworkDriver {

    String network();

    /** Publish a post and return the platform post ID. Throws on failure. */
    String publish(SocialAccount account, SocialPost post) throws Exception;

    /** Fetch basic account info after OAuth: {account_id, name, picture_url}. */
    Map<String, Object> fetchAccountInfo(String accessToken) throws Exception;
}
