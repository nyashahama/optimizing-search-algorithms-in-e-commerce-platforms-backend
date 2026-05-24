package com.nyasha.store.controllers;

import com.nyasha.store.dtos.returns.CreateReturnRequest;
import com.nyasha.store.entities.Return;
import com.nyasha.store.entities.User;
import com.nyasha.store.services.ReturnService;
import com.nyasha.store.services.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReturnsControllerTest {

    private final ReturnService returnService = mock(ReturnService.class);
    private final UserService userService = mock(UserService.class);
    private final ReturnsController returnsController = new ReturnsController(returnService, userService);

    @Test
    void returnsRoutesDelegateToService() {
        Authentication auth = new UsernamePasswordAuthenticationToken("buyer@example.com", "secret");
        Return expected = new Return();
        CreateReturnRequest request = new CreateReturnRequest(15L, "defect");
        Return listed = new Return();
        when(userService.getUserByEmail("buyer@example.com")).thenReturn(Optional.of(user(9L)));
        when(returnService.openReturn(9L, 3L, request)).thenReturn(expected);
        when(returnService.getReturnsForUser(9L)).thenReturn(List.of(listed));
        when(returnService.approveReturn(1L)).thenReturn(expected);
        when(returnService.rejectReturn(1L)).thenReturn(expected);
        when(returnService.refundReturn(1L, 9L, "refund-key")).thenReturn(expected);

        assertThat(returnsController.openReturn(auth, 3L, request).getBody()).isSameAs(expected);
        assertThat(returnsController.myReturns(auth)).containsExactly(listed);
        assertThat(returnsController.approve(1L).getBody()).isSameAs(expected);
        assertThat(returnsController.reject(1L).getBody()).isSameAs(expected);
        assertThat(returnsController.refund(1L, "refund-key").getBody()).isSameAs(expected);
    }

    private User user(long id) {
        User user = new User();
        user.setUserId(id);
        return user;
    }
}
