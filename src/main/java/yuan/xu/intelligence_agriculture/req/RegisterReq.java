package yuan.xu.intelligence_agriculture.req;

import lombok.Data;

@Data
public class RegisterReq {
    private String phone;
    private String username;
    private String code;
    private String password;
    private String confirmPassword;
}
