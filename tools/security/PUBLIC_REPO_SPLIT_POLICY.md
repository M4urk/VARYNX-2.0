# Public/Private Repository Split Policy

This policy defines what is allowed in the public VARYNX repository.

## Objective

Prevent publication of proprietary VARYNX engine/security implementation and any sensitive operational material.

## Private Repository Content (must NOT be public)

- Core guardian engines and scoring internals
- Security and trust implementation internals
- Private model/data and tuning artifacts
- Signing material and release credentials
- Internal operational scripts and private infrastructure details

## Public Repository Content (allowed)

- Public-safe documentation
- Public UI shell and non-sensitive presentation assets
- Public API contracts at a high level (no secret internals)
- Open build scaffolding that contains no private logic or credentials

## Required Process

1. Develop in private source repositories.
2. Export only approved public-safe files into the public repository.
3. Run the public safety scanner before push.
4. Push only after scanner passes.

## Enforcement

- Public safety CI workflow blocks merges when unsafe markers are found.
- Local pre-push checks should run `tools/security/public_safety_scan.py`.

## Immediate Rules

- Never commit secrets, tokens, or signing artifacts.
- Never commit machine-specific personal paths.
- Never push private engine/security logic to public remotes.
