package com.commercex.common.error;

public class NotFoundException extends BusinessException {

    public NotFoundException(String resource, Object identifier) {
        super(ErrorCode.NOT_FOUND, resource + " '" + identifier + "' was not found");
    }
}
