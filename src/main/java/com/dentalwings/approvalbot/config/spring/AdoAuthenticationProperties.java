package com.dentalwings.approvalbot.config.spring;

public class AdoAuthenticationProperties
{

    private AdoAuthenticationMode mode = AdoAuthenticationMode.PAT;
    private String bearerToken;

    public AdoAuthenticationMode getMode()
    {
        return mode;
    }

    public void setMode(AdoAuthenticationMode mode)
    {
        this.mode = mode == null ? AdoAuthenticationMode.PAT : mode;
    }

    public String getBearerToken()
    {
        return bearerToken;
    }

    public void setBearerToken(String bearerToken)
    {
        this.bearerToken = bearerToken;
    }
}
