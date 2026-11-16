// Starts the real backend on http://127.0.0.1:<port> for the Android tests.
//
//   dotnet run --project testing/local-api-host -- --port 8000 --out testing/build/local-api.json
//
// When the API is ready it writes <out>: the base URL plus two short-lived
// tokens signed with a key made fresh for this run (one for the app, one for
// reading records back the way the dashboard does). Nothing secret is stored
// in the repo. Stop with Ctrl+C; the database container is removed on exit.

using System.Security.Cryptography;
using System.Text.Json;
using ImmatureBackend.Application.Interfaces;
using ImmatureBackend.Infrastructure.Persistence;
using LocalApiHost;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.AspNetCore.TestHost;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.DependencyInjection.Extensions;
using Microsoft.IdentityModel.JsonWebTokens;
using Microsoft.IdentityModel.Tokens;
using Testcontainers.PostgreSql;

const string issuerBase = "https://local-test.supabase.invalid";

var port = int.Parse(ArgValue("--port") ?? "8000");
var outPath = Path.GetFullPath(ArgValue("--out") ?? "local-api.json");

var signingKey = new SymmetricSecurityKey(RandomNumberGenerator.GetBytes(32));

Console.WriteLine("Starting PostgreSQL container...");
await using var postgres = new PostgreSqlBuilder("postgres:17-alpine").Build();
await postgres.StartAsync();

Environment.SetEnvironmentVariable("ASPNETCORE_ENVIRONMENT", "Testing");
Environment.SetEnvironmentVariable("Database__ConnectionString", postgres.GetConnectionString());
Environment.SetEnvironmentVariable("Supabase__Url", issuerBase);
Environment.SetEnvironmentVariable("Cloudinary__Cloudname", "local");
Environment.SetEnvironmentVariable("Cloudinary__ApiKey", "local");
Environment.SetEnvironmentVariable("Cloudinary__ApiSecret", "local");
Environment.SetEnvironmentVariable("Sentry__Dsn", "");

await using var factory = new LocalApiFactory(signingKey);
factory.UseKestrel(port);
factory.StartServer();

await using (var scope = factory.Services.CreateAsyncScope())
{
    await scope.ServiceProvider.GetRequiredService<AppDbContext>().Database.MigrateAsync();
}

var baseUrl = $"http://127.0.0.1:{port}";
Directory.CreateDirectory(Path.GetDirectoryName(outPath)!);
await File.WriteAllTextAsync(outPath, JsonSerializer.Serialize(new
{
    baseUrl,
    // The app signs in anonymously, so its test token is anonymous too.
    captureToken = Token(new { provider = "anonymous" }, isAnonymous: true),
    dashboardToken = Token(new { provider = "email", app_role = "dashboard" }, isAnonymous: false),
    startedAt = DateTimeOffset.UtcNow
}));

Console.WriteLine($"Local API ready at {baseUrl} (session file: {outPath})");

var stop = new TaskCompletionSource();
Console.CancelKeyPress += (_, e) => { e.Cancel = true; stop.TrySetResult(); };
AppDomain.CurrentDomain.ProcessExit += (_, _) => stop.TrySetResult();
await stop.Task;

if (File.Exists(outPath)) File.Delete(outPath);
return;

string Token(object appMetadata, bool isAnonymous)
{
    var payload = JsonSerializer.Serialize(new Dictionary<string, object>
    {
        ["iss"] = $"{issuerBase}/auth/v1",
        ["aud"] = "authenticated",
        ["sub"] = Guid.NewGuid().ToString(),
        ["exp"] = DateTimeOffset.UtcNow.AddHours(2).ToUnixTimeSeconds(),
        ["is_anonymous"] = isAnonymous,
        ["app_metadata"] = appMetadata
    });

    return new JsonWebTokenHandler().CreateToken(
        payload, new SigningCredentials(signingKey, SecurityAlgorithms.HmacSha256));
}

string? ArgValue(string name)
{
    var i = Array.IndexOf(args, name);
    return i >= 0 && i + 1 < args.Length ? args[i + 1] : null;
}

namespace LocalApiHost
{
    internal sealed class LocalApiFactory(SecurityKey signingKey)
        : WebApplicationFactory<ImmatureBackend.Api.Program>
    {
        protected override void ConfigureWebHost(IWebHostBuilder builder)
        {
            builder.UseEnvironment("Testing");

            builder.ConfigureTestServices(services =>
            {
                // Check tokens against this run's key instead of fetching keys from Supabase.
                services.PostConfigure<JwtBearerOptions>(JwtBearerDefaults.AuthenticationScheme, options =>
                {
                    options.ConfigurationManager = null;
                    options.TokenValidationParameters.IssuerSigningKey = signingKey;
                });

                // Cloudinary is replaced; the ONNX grain detector stays real.
                services.RemoveAll<IImageService>();
                services.AddScoped<IImageService, LocalImageStore>();
            });
        }
    }
}
