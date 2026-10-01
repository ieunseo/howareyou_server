package org.example.howareyou.domain.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class CreateGroupChatRoomRequest {
    // 그룹채팅을 위해 추가
    @NotBlank // 공백까지X
    private String title; // 그룹채팅방 명
    @NotEmpty // 공백Ok 빈문자열X
    private List<String> inviteeMembernames; // invitee는 초대받는 사람, 초대할 멤버 이름 목록 (나 제외)
}
