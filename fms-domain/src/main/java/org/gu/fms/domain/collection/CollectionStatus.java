package org.gu.fms.domain.collection;

/** Un versement annulé par un avoir passe à CREDITED ; il reste visible, l'avoir le compense. */
public enum CollectionStatus {
    VALID,
    CREDITED
}
