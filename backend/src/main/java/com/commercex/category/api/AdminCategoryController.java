package com.commercex.category.api;

import com.commercex.category.application.CategoryCommand;
import com.commercex.category.application.CategoryService;
import com.commercex.category.application.CategoryView;
import com.commercex.common.security.CurrentUser;
import com.commercex.common.text.Slugs;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/categories")
@PreAuthorize("hasAnyRole('ADMIN', 'CATALOG_MANAGER')")
@Tag(name = "Admin: Catalog")
@SecurityRequirement(name = "bearer")
class AdminCategoryController {

    private final CategoryService categoryService;

    AdminCategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /** {@code version} is required on update (optimistic locking) and ignored on create. */
    record CategoryRequest(
            Long parentId,
            @NotBlank @Size(max = 120) String name,
            @Size(max = Slugs.MAX_LENGTH) @Pattern(regexp = Slugs.PATTERN) String slug,
            @Size(max = 2000) String description,
            @Min(0) @Max(10_000) int position,
            Boolean active,
            Long version) {

        CategoryCommand command() {
            return new CategoryCommand(parentId, name, slug, description, position, active == null || active,
                    version);
        }
    }

    @GetMapping
    List<CategoryView> list() {
        return categoryService.listAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CategoryView create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CategoryRequest body) {
        return categoryService.create(CurrentUser.id(jwt), body.command());
    }

    @PutMapping("/{id}")
    CategoryView update(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
            @Valid @RequestBody CategoryRequest body) {
        return categoryService.update(CurrentUser.id(jwt), id, body.command());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        categoryService.delete(CurrentUser.id(jwt), id);
    }
}
