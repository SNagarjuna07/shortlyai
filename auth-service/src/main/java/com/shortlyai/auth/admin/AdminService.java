package com.shortlyai.auth.admin;

import com.shortlyai.auth.dto.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminService {

    Page<UserResponse> getAllUsers(Pageable pageable);
}
