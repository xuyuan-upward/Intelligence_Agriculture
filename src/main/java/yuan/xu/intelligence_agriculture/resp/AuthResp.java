package yuan.xu.intelligence_agriculture.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthResp {
    private String token;
    private String role;
    private Long userId;
    private String username;
}
