package yuan.xu.intelligence_agriculture.req;

import lombok.Data;

@Data
public class LoginReq {
    private String phone;
    private String password;
    private String code;
    private String loginType;
}
