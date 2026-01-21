package yuan.xu.intelligence_agriculture.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserResp {
    private Long id;
    private String phone;
    private String username;
    private String role;
    private Date createTime;
}
