package com.yizhaoqi.smartpai.config;

import com.yizhaoqi.smartpai.model.RegistrationMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.auth")
public class AppAuthProperties {

    private final Registration registration = new Registration();

    public Registration getRegistration() {
        return registration;
    }

    public static class Registration {
        private RegistrationMode mode = RegistrationMode.INVITE_ONLY;
        private boolean inviteRequired = true;

        public RegistrationMode getMode() {
            return mode;
        }

        public void setMode(RegistrationMode mode) {
            this.mode = mode;
        }

        public boolean isInviteRequired() {
            return inviteRequired;
        }

        public void setInviteRequired(boolean inviteRequired) {
            this.inviteRequired = inviteRequired;
        }
    }
}
