package yuan.xu.intelligence_agriculture.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

public class GscProductLocationEnum {

    /**
     * 地点类型
     **/
    @AllArgsConstructor
    public enum LocationType {
        /**
         * 0:仓库，1:门店
         */
        STOREHOUSE(0, "仓库"),
        STORE(1, "门店");

        @Getter
        private int code;
        @Getter
        private String msg;
    }

}
