package com.sirolf2009.grossrecipes

import com.sirolf2009.grossrecipes.events.EventsModule
import com.sirolf2009.grossrecipes.list.ListModule
import com.sirolf2009.modulith.account.AccountModule
import com.sirolf2009.modulith.account.VerifyToken
import com.sirolf2009.modulith.cqrs.execute
import spark.kotlin.before
import spark.kotlin.halt
import spark.kotlin.port

fun main() {
    port(4567)
    before {
        response.header("Access-Control-Allow-Origin", "*")
        if (request.headers().contains("Authorization")) {
            val validToken = execute(
                VerifyToken(
                    request.headers("Authorization").replace("Bearer ", "")
                )
            )
            if (validToken == null) {
                halt(498, "Invalid Token")
            }
        }
    }

    AccountModule.apply {
        issuer = "gross-recipes"
        publicPem = "src/main/resources/public.pem"
        privatePem = "src/main/resources/private.pem"
        hibernateConfiguration = "src/main/resources/hibernate.cfg.xml"
        ignite()
    }
    ListModule.ignite()
    EventsModule.ignite()
}