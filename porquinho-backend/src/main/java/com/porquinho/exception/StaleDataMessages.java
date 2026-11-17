package com.porquinho.exception;

/**
 * 409 messages for data changed in another screen (retro do Epic 4): the stale account and card edits (explicit
 * version check in AccountService/CreditCardService and a collision at flush) and a collision on any other entity.
 */
public final class StaleDataMessages {

    public static final String ACCOUNT = "Esta conta foi alterada em outra tela. Abra a edição de novo.";
    public static final String CARD = "Este cartão foi alterado em outra tela. Abra a edição de novo.";
    public static final String GENERIC = "Os dados foram alterados em outra tela. Recarregue e tente de novo.";

    private StaleDataMessages() {
    }
}
