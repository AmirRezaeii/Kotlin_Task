import kotlinx.coroutines.runBlocking
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.Response
import java.util.Scanner

data class GitHubUserResponse(
    val login: String,
    val public_repos: Int,
    val created_at: String,
    val followers: Int,
    val following: Int
)

data class GitHubRepoResponse(
    val name: String,
    val html_url: String,
    val description: String?
)

data class GitHubUser(
    val login: String,
    val publicRepos: Int,
    val createdAt: String,
    val followers: Int,
    val following: Int,
    val repos: List<GitHubRepoResponse>
)

interface GitHubService {
    @GET("users/{username}")
    suspend fun getUser(
        @Path("username") username: String
    ): Response<GitHubUserResponse>

    @GET("users/{username}/repos")
    suspend fun getRepos(
        @Path("username") username: String
    ): Response<List<GitHubRepoResponse>>
}

fun printUserInfo(user: GitHubUser) {
    println("=== User Info for ${user.login} ===")
    println("Public repos: ${user.publicRepos}")
    println("Account created at: ${user.createdAt}")
    println("Followers: ${user.followers}")
    println("Following: ${user.following}")
    println("Repositories:")
    user.repos.forEach { repo ->
        println("- ${repo.name}: ${repo.html_url}")
    }
    println()
}

fun main() = runBlocking {
    val retrofit = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    val service = retrofit.create(GitHubService::class.java)
    val scanner = Scanner(System.`in`)
    val cache = mutableMapOf<String, GitHubUser>()

    while (true) {
        println(
            """
            |=== GitHub CLI Menu ===
            |1. Fetch user info
            |2. List cached users
            |3. Search cached users
            |4. Search cached repositories
            |5. Exit
            """.trimMargin()
        )
        print("Enter your choice: ")
        when (scanner.nextLine().trim()) {
            "1" -> {
                print("Enter GitHub username: ")
                val username = scanner.nextLine().trim()
                if (cache.containsKey(username)) {
                    println("User '$username' is already cached.")
                    printUserInfo(cache[username]!!)
                } else {
                    try {
                        val userResp = service.getUser(username)
                        if (!userResp.isSuccessful) {
                            println("Error fetching user: ${userResp.code()} ${userResp.message()}\n")
                            continue
                        }
                        val userData = userResp.body()!!
                        val reposResp = service.getRepos(username)
                        if (!reposResp.isSuccessful) {
                            println("Error fetching repos: ${reposResp.code()} ${reposResp.message()}\n")
                            continue
                        }
                        val reposData = reposResp.body()!!
                        val user = GitHubUser(
                            login = userData.login,
                            publicRepos = userData.public_repos,
                            createdAt = userData.created_at,
                            followers = userData.followers,
                            following = userData.following,
                            repos = reposData
                        )
                        cache[username] = user
                        printUserInfo(user)
                    } catch (e: Exception) {
                        println("Exception occurred: ${e.message}\n")
                    }
                }
            }
            "2" -> {
                if (cache.isEmpty()) {
                    println("No users cached.\n")
                } else {
                    println("Cached users:")
                    cache.keys.forEach { println("- $it") }
                    println()
                }
            }
            "3" -> {
                print("Enter search term for username: ")
                val term = scanner.nextLine().trim().lowercase()
                val matches = cache.keys.filter { it.lowercase().contains(term) }
                if (matches.isEmpty()) {
                    println("No matching users found.\n")
                } else {
                    println("Matching users:")
                    matches.forEach { println("- $it") }
                    println()
                }
            }
            "4" -> {
                print("Enter search term for repository name: ")
                val term = scanner.nextLine().trim().lowercase()
                val results = cache.values.flatMap { user ->
                    user.repos.filter { it.name.lowercase().contains(term) }
                        .map { "${user.login}/${it.name} -> ${it.html_url}" }
                }
                if (results.isEmpty()) {
                    println("No matching repositories found.\n")
                } else {
                    println("Matching repositories:")
                    results.forEach { println("- $it") }
                    println()
                }
            }
            "5" -> {
                println("Goodbye!")
                break
            }
            else -> {
                println("Invalid choice. Please try again.\n")
            }
        }
    }
}
