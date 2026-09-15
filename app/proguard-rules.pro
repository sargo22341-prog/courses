# Hilt, Room, Retrofit, OkHttp and Kotlin serialization ship their own consumer rules.
# Keep this file narrow: any new reflection-based integration must add and justify its rules here.

# Retrofit reads the response type of a suspend method from the generic signature of its
# Continuation parameter, which R8 does not count as a use. A response class whose value the app
# never reads (ApiStatusDto, "Tester la connexion") was removed, the signature fell back to Object
# and every release build failed with "Unable to create converter". Network DTOs are kept (their
# names can still be obfuscated). Guarded by RetrofitKeepRulesTest.
-keep,allowobfuscation @kotlinx.serialization.Serializable class org.opensources.courses.**.data.remote.**
