package com.hmdp.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.util.Calendar;
import java.util.Date;

public class tokenUtils {

    private static final String issurer = "签发者";

    //创建令牌方法
    public static String generateToken(String userId) throws Exception {

        //加密算法
        Algorithm algorithm = Algorithm.RSA256(RSAUtil.getPublicKey(), RSAUtil.getPrivateKey());

        //过期时间
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.MINUTE,1);

        return JWT.create().withKeyId(userId)
                           .withIssuer(issurer)
                            .withExpiresAt(calendar.getTime())
                            .sign(algorithm);

    }
    public static String generateRefreshToken(String userId) throws Exception {
        //加密算法
        Algorithm algorithm = Algorithm.RSA256(RSAUtil.getPublicKey(), RSAUtil.getPrivateKey());

        //过期时间
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.DAY_OF_MONTH,7);

        return JWT.create().withKeyId(userId)
                .withIssuer(issurer)
                .withExpiresAt(calendar.getTime())
                .sign(algorithm);
    }



    //解密算法
    public static String verifyToken(String token){
        try {
            //加密算法
            Algorithm algorithm = Algorithm.RSA256(RSAUtil.getPublicKey(), RSAUtil.getPrivateKey());

            JWTVerifier verifier = JWT.require(algorithm).build();

            DecodedJWT jwtt = verifier.verify(token);

            String userID = jwtt.getKeyId();

            return  userID;

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }


}
