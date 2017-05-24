package top.jyx365.authserver.config.weixin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.security.oauth2.resource.AuthoritiesExtractor;
import org.springframework.boot.autoconfigure.security.oauth2.resource.FixedAuthoritiesExtractor;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;

import org.springframework.security.oauth2.client.OAuth2RestOperations;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.security.oauth2.client.resource.BaseOAuth2ProtectedResourceDetails;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.common.exceptions.InvalidTokenException;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.OAuth2Request;
import org.springframework.security.oauth2.provider.token.ResourceServerTokenServices;

import org.springframework.web.util.UriComponentsBuilder;

import top.jyx365.authserver.config.weixin.WeixinUserInfo;
import top.jyx365.authserver.domain.User;
import top.jyx365.authserver.service.UserService;


@Slf4j
public class WeixinUserInfoTokenServices implements ResourceServerTokenServices {


    private final String userInfoEndpointUrl;

    private final String clientId;

    private OAuth2RestOperations restTemplate;

    private String tokenType = DefaultOAuth2AccessToken.BEARER_TYPE;

    private UserService userService;

    private AuthoritiesExtractor authoritiesExtractor = new FixedAuthoritiesExtractor();

    public WeixinUserInfoTokenServices ( String userInfoEndpointUrl, String clientId ) {
        this.userInfoEndpointUrl = userInfoEndpointUrl;
        this.clientId = clientId;
    }

    public void setRestTemplate(OAuth2RestOperations restTemplate) {
        this.restTemplate = restTemplate;
    }


    public void setUserService(UserService userService) {
        log.debug("setUserService:{}", userService);
        this.userService = userService;
    }


    @Override
    public OAuth2Authentication loadAuthentication(String accessToken) throws AuthenticationException, InvalidTokenException
    {
        WeixinUserInfo map = getMap(this.userInfoEndpointUrl, accessToken);
        if (map.getErrcode() != null) {
            if (log.isDebugEnabled()) {
                log.debug("userinfo returned error: " + map.getErrmsg());
            }
            throw new InvalidTokenException(accessToken);
        }
        return extractAuthentication(map);
    }



    private OAuth2Authentication extractAuthentication(WeixinUserInfo map) {
        User user = userService.weixinUserLogin(map);
        //User user = new User();
        Long principal = user.getId();
        List<GrantedAuthority> authorities = user.getGrantedAuthorities();
        //List<GrantedAuthority> authorities = new ArrayList<GrantedAuthority>();
        OAuth2Request request = new OAuth2Request(null, this.clientId, null, true, null,
                null, null, null, null);
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                principal, "N/A", authorities);
        //token.setDetails(user);
        return new OAuth2Authentication(request, token);
    }

    @SuppressWarnings({"unchecked"})
    private WeixinUserInfo getMap(String path, String accessToken) {
        if (log.isDebugEnabled()) {
            log.debug("Getting user info from: " + path);
        }
        try {
            OAuth2RestOperations restTemplate = this.restTemplate;
            if (restTemplate == null) {
                BaseOAuth2ProtectedResourceDetails resource = new BaseOAuth2ProtectedResourceDetails();
                resource.setClientId(this.clientId);
                restTemplate = new OAuth2RestTemplate(resource);
            }
            OAuth2AccessToken existingToken = restTemplate.getOAuth2ClientContext()
                .getAccessToken();
            if (existingToken == null || !accessToken.equals(existingToken.getValue())) {
                DefaultOAuth2AccessToken token = new DefaultOAuth2AccessToken(
                        accessToken);
                token.setTokenType(this.tokenType);
                restTemplate.getOAuth2ClientContext().setAccessToken(token);
            }

            UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(path);
            builder.queryParam("access_token",accessToken)
                .queryParam("openid",existingToken.getAdditionalInformation().get("openid"))
                .queryParam("lang","zh_CN");

            return restTemplate.getForEntity(builder.toUriString(), WeixinUserInfo.class).getBody();
        }
        catch (Exception ex) {
            log.warn("Could not fetch user details: " + ex.getClass() + ", "
                    + ex.getMessage());
            WeixinUserInfo ret = new WeixinUserInfo();
            ret.setErrcode("error");
            ret.setErrmsg("Could not fetch user details");
            return ret;
        }
    }

    @Override
    public OAuth2AccessToken readAccessToken(String accessToken) {
        throw new UnsupportedOperationException("Not supported: read access token");

    }

}
