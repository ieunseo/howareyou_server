package org.example.howareyou.domain.chat.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class InviteGroupMembersRequest {
    @NotEmpty // null X, [] X
    private List<String> inviteeMembernames; // 추가로 초대할 멤버 이름 목록
}
