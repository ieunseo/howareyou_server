package org.example.howareyou.domain.quiz.service;

import org.example.howareyou.domain.member.service.MemberService;
import org.example.howareyou.domain.quiz.dto.VocaDTO;
import org.example.howareyou.domain.quiz.entity.QuizResult;
import org.example.howareyou.domain.quiz.repository.*;
import org.example.howareyou.domain.vocabulary.document.MemberVocabulary;
import org.example.howareyou.domain.vocabulary.repository.MemberVocabularyRepository;
import org.example.howareyou.domain.vocabulary.service.MemberVocaBookService;
import org.example.howareyou.global.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QuizGeneratorServiceImplTest {
    private final MemberService members = mock(MemberService.class);
    private final MemberVocaBookService books = mock(MemberVocaBookService.class);
    private final QuizResultRepository results = mock(QuizResultRepository.class);
    private final QuizWordRepository savedWords = mock(QuizWordRepository.class);
    private final QuizVocaRepository words = mock(QuizVocaRepository.class);
    private final MemberVocabularyRepository counts = mock(MemberVocabularyRepository.class);
    private final QuizGeneratorServiceImpl service = new QuizGeneratorServiceImpl(members, books, results, savedWords, words, counts);

    @BeforeEach void setUp() {
        when(members.getIdByMembername("test")).thenReturn(1L);
        when(results.save(any(QuizResult.class))).thenAnswer(i -> i.getArgument(0));
    }
    private List<VocaDTO> pool(int meaningCount) {
        return IntStream.range(0, 30).mapToObj(i -> VocaDTO.builder().word("word" + i)
                .meaning("meaning" + (i % meaningCount)).level("a1").pos("noun").build()).toList();
    }
    @Test void randomReadsCandidatesOnceWithoutCountingAndKeepsFourDistinctChoices() {
        when(words.findWords("test", null, null, 0, 1000)).thenReturn(pool(4));
        var response = service.startRandomQuiz("test", null);
        assertTrue(response.getQuizQuestions().size() >= 5 && response.getQuizQuestions().size() <= 30);
        response.getQuizQuestions().forEach(q -> {
            assertEquals(4, new HashSet<>(q.getChoices()).size());
            int index = Integer.parseInt(q.getQuestion().substring(4));
            assertTrue(q.getChoices().contains("meaning" + index % 4));
        });
        verify(words).findWords("test", null, null, 0, 1000);
        verifyNoInteractions(counts);
    }
    @Test void dailyReusesFullPoolWithoutCounting() {
        var date = LocalDate.of(2026,10,5);
        var daily = IntStream.range(0,5).mapToObj(i -> MemberVocabulary.MemberWordEntry.builder()
                .word("word"+i).meaning("meaning0").level("a1").pos("noun").build()).toList();
        when(books.findWordsByMemberAndDatePaged("test",date,0,1000,"analyzedAt","desc")).thenReturn(new PageImpl<>(daily));
        when(words.findWords("test",null,null,0,1000)).thenReturn(pool(4));
        var response = service.startDailyQuiz("test",date);
        assertEquals(5,response.getQuizQuestions().size());
        response.getQuizQuestions().forEach(q -> assertEquals(4,new HashSet<>(q.getChoices()).size()));
        verify(words).findWords("test",null,null,0,1000);
        verifyNoInteractions(counts);
    }
    @Test void insufficientMeaningsStillFailsBeforeSaving() {
        when(words.findWords("test",null,null,0,1000)).thenReturn(pool(3));
        assertThrows(CustomException.class, () -> service.startRandomQuiz("test",null));
        verifyNoInteractions(results,savedWords,counts);
    }
}
