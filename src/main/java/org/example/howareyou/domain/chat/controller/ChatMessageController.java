package org.example.howareyou.domain.chat.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.howareyou.domain.chat.websocket.dto.ChatMessageDocumentResponse;
import org.example.howareyou.domain.chat.websocket.dto.ChatMessageResponse;
import org.example.howareyou.domain.chat.websocket.dto.CreateChatMessageRequest;
import org.example.howareyou.domain.chat.websocket.service.ChatMessageService;
import org.example.howareyou.global.security.CustomMemberDetails;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = "채팅 메시지", description = "채팅방 메시지 관련 API")
@RequestMapping("/api/chat-message")
public class ChatMessageController {

  private final ChatMessageService chatMessageService;

  @Operation(
      summary = "최근 메시지 조회",
      description = "지정된 채팅방에서 가장 최근의 메시지 N개를 조회합니다. (Redis 우선 조회)"
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "조회 성공",
          content = @Content(schema = @Schema(implementation = ChatMessageDocumentResponse.class))),
      @ApiResponse(responseCode = "404", description = "채팅방 없음"),
      @ApiResponse(responseCode = "500", description = "서버 오류")
  })
  @GetMapping("/{chatRoomId}/recent")
  public List<ChatMessageDocumentResponse> getRecentMessages(
      @PathVariable String chatRoomId,
      @RequestParam(defaultValue = "30") int count,
      @AuthenticationPrincipal CustomMemberDetails memberDetails
  ) {
    Long memberId = memberDetails.getId();
    return chatMessageService.getRecentMessagesWithFallback(chatRoomId, count,memberId);
  }

  @Operation(
      summary = "이전 메시지 페이징 조회",
      description = "무한스크롤 방식으로 특정 시간 이전의 메시지들을 조회합니다."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "조회 성공",
          content = @Content(schema = @Schema(implementation = ChatMessageDocumentResponse.class))),
      @ApiResponse(responseCode = "400", description = "잘못된 시간 형식"),
      @ApiResponse(responseCode = "500", description = "서버 오류")
  })
  @GetMapping("/{chatRoomId}/previous")
  public List<ChatMessageDocumentResponse> getPreviousMessages(
      @PathVariable String chatRoomId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant before,
      @RequestParam(defaultValue = "30") int size,
      @AuthenticationPrincipal CustomMemberDetails memberDetails
  ) {
    Long memberId = memberDetails.getId();
    return chatMessageService.getPreviousMessages(chatRoomId, before, size,memberId);
  }

  @Operation(
      summary = "메시지 읽음 처리",
      description = "채팅방에서 사용자 기준으로 메시지를 읽음 처리합니다."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "읽음 처리 완료"),
      @ApiResponse(responseCode = "401", description = "인증 실패"),
      @ApiResponse(responseCode = "500", description = "서버 오류")
  })
  @PostMapping("/{chatRoomId}/read")
  public void markMessagesAsRead(
      @PathVariable String chatRoomId,
      @AuthenticationPrincipal CustomMemberDetails memberDetails
  ) {
    String userId = memberDetails.getId().toString();
    chatMessageService.markMessagesAsRead(chatRoomId, userId);
  }


}
