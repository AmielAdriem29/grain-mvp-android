using System.Security.Cryptography;
using FluentResults;
using ImmatureBackend.Application.Interfaces;
using Microsoft.AspNetCore.Http;

namespace LocalApiHost;

/// <summary>
/// Takes the place of Cloudinary for test runs. It hashes the exact bytes the
/// API received and puts the SHA-256 in the public id and URL, so a test can
/// read the stored record back and check the image arrived byte for byte
/// (TC-INT-04). The image is not kept anywhere.
/// </summary>
public sealed class LocalImageStore : IImageService
{
    public async Task<Result<(string publicId, string secureUrl)>> UploadAsync(IFormFile file,
        CancellationToken cancellationToken = default)
    {
        await using var stream = file.OpenReadStream();
        var hash = Convert.ToHexStringLower(await SHA256.HashDataAsync(stream, cancellationToken));
        var publicId = $"local/{hash}";

        return Result.Ok((publicId, secureUrl: $"https://local-image-store.invalid/{publicId}.jpg"));
    }
}
