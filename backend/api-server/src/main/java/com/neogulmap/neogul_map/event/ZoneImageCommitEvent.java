package com.neogulmap.neogul_map.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Zone 이미지 커밋 이벤트
 * event 패키지에 위치 - MVC 패턴의 이벤트 처리
 */
@Getter
public class ZoneImageCommitEvent extends ApplicationEvent {
    
    private final String tempFileName;
    private final String finalFileName;
    private final Integer zoneId;

    public ZoneImageCommitEvent(Object source, String tempFileName, String finalFileName, Integer zoneId) {
        super(source);
        this.tempFileName = tempFileName;
        this.finalFileName = finalFileName;
        this.zoneId = zoneId;
    }

    public static ZoneImageCommitEvent create(String tempFileName, String finalFileName, Integer zoneId) {
        return new ZoneImageCommitEvent(new Object(), tempFileName, finalFileName, zoneId);
    }
}
