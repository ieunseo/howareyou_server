package org.example.howareyou.domain.chat.controller;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import jakarta.validation.Valid;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.howareyou.domain.chat.dto.ChatRequestSummaryResponse;
import org.example.howareyou.domain.chat.dto.ChatRoomResponse;
import org.example.howareyou.domain.chat.dto.ChatRoomSummaryResponse;
import org.example.howareyou.domain.chat.dto.CreateChatRoomRequest;
import org.example.howareyou.domain.chat.dto.CreateChatRoomResponse;
import org.example.howareyou.domain.chat.dto.CreateGroupChatRoomRequest;
import org.example.howareyou.domain.chat.dto.GroupInvitationResponse;
import org.example.howareyou.domain.chat.dto.InviteGroupMembersRequest;
import org.example.howareyou.domain.chat.service.ChatRoomService;
import org.example.howareyou.domain.member.service.MemberService;
import org.example.howareyou.global.security.CustomMemberDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = "채팅방", description = "채팅방 생성, 수락/거절, 조회 등 관련 API")
@RequestMapping("/api/chat-room")
public class ChatRoomController {

  private final ChatRoomService chatRoomService;
  private final MemberService memberService;

  @Operation(summary = "채팅방 생성", description = "상대방과의 1:1 채팅방을 생성합니다.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "채팅방 생성 성공",
          content = @Content(schema = @Schema(implementation = CreateChatRoomResponse.class))),
      @ApiResponse(responseCode = "400", description = "잘못된 요청"),
      @ApiResponse(responseCode = "500", description = "서버 오류")
  })
  @PostMapping("/create")
  public CreateChatRoomResponse createChatRoom(
      @RequestBody CreateChatRoomRequest request,
      @Parameter(hidden = true)
      @AuthenticationPrincipal CustomMemberDetails memberDetails
  ) {
    log.info("request 입니다 : {}", request);
    Long senderId = memberDetails.getId();
    return chatRoomService.createChatRoom(request, senderId);
  }

  @Operation(summary = "그룹 채팅방 생성", description = "여러 명을 초대해 그룹 채팅방을 생성합니다.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "그룹 채팅방 생성 성공",
          content = @Content(schema = @Schema(implementation = CreateChatRoomResponse.class))),
      @ApiResponse(responseCode = "400", description = "잘못된 요청 (제목 없음, 초대 대상 없음)"),
      @ApiResponse(responseCode = "404", description = "존재하지 않는 사용자")
  })
  @PostMapping("/group")
  public CreateChatRoomResponse createGroupChatRoom(
      @Valid @RequestBody CreateGroupChatRoomRequest request,
      @Parameter(hidden = true)
      @AuthenticationPrincipal CustomMemberDetails memberDetails
  ) {
    Long ownerId = memberDetails.getId();
    return chatRoomService.createGroupChatRoom(request, ownerId);
  }

  @Operation(summary = "그룹 채팅방 추가 초대", description = "참여 중인 그룹방에 다른 사용자를 초대합니다. 나갔거나 거절한 사람도 다시 초대할 수 있습니다.")
  @PostMapping("/group/{roomUuid}/invite")
  public void inviteToGroupChatRoom(
      @Parameter(description = "채팅방 UUID", required = true)
      @PathVariable String roomUuid,
      @Valid @RequestBody InviteGroupMembersRequest request,
      @Parameter(hidden = true)
      @AuthenticationPrincipal CustomMemberDetails memberDetails
  ) {
    chatRoomService.inviteToGroupChatRoom(roomUuid, request, memberDetails.getId());
  }

  @Operation(summary = "그룹 초대 수락", description = "받은 그룹 채팅 초대를 수락합니다.")
  @PostMapping("/group/{roomUuid}/accept")
  public void acceptGroupInvitation(
      @Parameter(description = "채팅방 UUID", required = true)
      @PathVariable String roomUuid,
      @Parameter(hidden = true)
      @AuthenticationPrincipal CustomMemberDetails memberDetails
  ) {
    chatRoomService.acceptGroupInvitation(roomUuid, memberDetails.getId());
  }

  @Operation(summary = "그룹 초대 거절", description = "받은 그룹 채팅 초대를 거절합니다. 방은 그대로 유지됩니다.")
  @PostMapping("/group/{roomUuid}/reject")
  public void rejectGroupInvitation(
      @Parameter(description = "채팅방 UUID", required = true)
      @PathVariable String roomUuid,
      @Parameter(hidden = true)
      @AuthenticationPrincipal CustomMemberDetails memberDetails
  ) {
    chatRoomService.rejectGroupInvitation(roomUuid, memberDetails.getId());
  }

  @Operation(summary = "그룹 채팅방 나가기", description = "그룹방에서 나갑니다. 방장이 나가면 가장 먼저 참여한 사람에게 방장이 넘어가고, 아무도 없으면 방이 종료됩니다.")
  @PostMapping("/group/{roomUuid}/leave")
  public void leaveGroupChatRoom(
      @Parameter(description = "채팅방 UUID", required = true)
      @PathVariable String roomUuid,
      @Parameter(hidden = true)
      @AuthenticationPrincipal CustomMemberDetails memberDetails
  ) {
    chatRoomService.leaveGroupChatRoom(roomUuid, memberDetails.getId());
  }

  @Operation(summary = "내가 받은 그룹 초대 목록", description = "수락 대기 중인 그룹 채팅 초대 목록을 조회합니다.")
  @GetMapping("/group/invitations")
  public List<GroupInvitationResponse> getMyGroupInvitations(
      @Parameter(hidden = true)
      @AuthenticationPrincipal CustomMemberDetails memberDetails
  ) {
    return chatRoomService.getMyGroupInvitations(memberDetails.getId());
  }

  @Operation(summary = "채팅방 수락", description = "상대방의 채팅 요청을 수락합니다.")
  @PostMapping("/{roomUuid}/accept")
  public void acceptChatRoom(
      @Parameter(description = "채팅방 UUID", required = true)
      @PathVariable String roomUuid,
      @AuthenticationPrincipal CustomMemberDetails memberDetails
  ) {
    Long receiverId = memberDetails.getId();
    chatRoomService.acceptChatRoom(roomUuid, receiverId);
  }

  @Operation(summary = "채팅방 거절", description = "상대방의 채팅 요청을 거절합니다.")
  @PostMapping("/{roomUuid}/reject")
  public void rejectChatRoom(
      @Parameter(description = "채팅방 UUID", required = true)
      @PathVariable String roomUuid,

      @Parameter(description = "거절하는 사용자 ID", required = true)
      @RequestParam Long receiverId
  ) {
    chatRoomService.rejectChatRoom(roomUuid, receiverId);
  }

  @Operation(summary = "단일 채팅방 조회", description = "특정 채팅방의 상세 정보를 조회합니다.")
  @ApiResponse(responseCode = "200", description = "조회 성공",
      content = @Content(schema = @Schema(implementation = ChatRoomResponse.class)))
  @GetMapping("/{roomUuid}")
  public ChatRoomResponse getChatRoom(
      @Parameter(description = "채팅방 UUID", required = true)
      @PathVariable String roomUuid,

      @Parameter(hidden = true)
      @AuthenticationPrincipal CustomMemberDetails memberDetails
  ) {
    Long myId = memberDetails.getId();
    return chatRoomService.getChatRoom(roomUuid, myId);
  }

  @Operation(summary = "내 채팅방 목록 조회", description = "현재 로그인한 사용자가 참여 중인 채팅방 목록을 조회합니다.")
  @ApiResponse(responseCode = "200", description = "조회 성공",
      content = @Content(schema = @Schema(implementation = ChatRoomSummaryResponse.class)))
  @GetMapping("/my-rooms")
  public List<ChatRoomSummaryResponse> getMyChatRooms(
      @Parameter(hidden = true)
      @AuthenticationPrincipal CustomMemberDetails memberDetails
  ) {
    Long myId = memberDetails.getId();
    return chatRoomService.getMyChatRooms(myId);
  }

  @Operation(summary = "채팅방 연결 해제 및 삭제", description = "현재 사용자가 채팅방에서 나가고 연결을 끊습니다.")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "삭제 성공"),
      @ApiResponse(responseCode = "404", description = "채팅방 없음")
  })
  @DeleteMapping("/{roomUuid}")
  public void disconnectChatRoom(
      @Parameter(description = "채팅방 UUID", required = true)
      @PathVariable String roomUuid,

      @Parameter(hidden = true)
      @AuthenticationPrincipal CustomMemberDetails memberDetails
  ) {
    Long myId = memberDetails.getId();
    chatRoomService.disconnectFromChatRoom(myId, roomUuid);
  }

  @Operation(summary = "내가 보낸 채팅 요청 목록", description = "현재 로그인한 사용자가 보낸(발신자) 채팅 요청 목록을 조회합니다.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "조회 성공")
  })
  @GetMapping("/requests/sent")
  public List<ChatRequestSummaryResponse> getMySentChatRequests(
      @Parameter(hidden = true)
      @AuthenticationPrincipal CustomMemberDetails memberDetails
  ) {
    Long myId = memberDetails.getId();
    return chatRoomService.getSentRequests(myId);
  }

  @Operation(summary = "내가 받은 채팅 요청 목록", description = "현재 로그인한 사용자가 받은(수신자) 채팅 요청 목록을 조회합니다.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "조회 성공")
  })
  @GetMapping("/requests/received")
  public List<ChatRequestSummaryResponse> getMyReceivedChatRequests(
      @Parameter(hidden = true)
      @AuthenticationPrincipal CustomMemberDetails memberDetails
  ) {
    Long myId = memberDetails.getId();
    return chatRoomService.getReceivedRequests(myId);
  }

}
