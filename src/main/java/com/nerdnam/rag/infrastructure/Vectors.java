package com.nerdnam.rag.infrastructure;

/** pgvector 값 문자열 ("[0.1,0.2,...]"). SQL 에서 CAST(:v AS public.vector) 로 바꾼다 */
public final class Vectors {

    private Vectors() {
    }

    public static String literal(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 10).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }
}
