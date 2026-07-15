# Contributing to cloud-itonami-isic-561

Thank you for your interest in contributing to the ISIC-561 restaurant operations coordination actor!

## Development Workflow

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/your-feature`)
3. Commit your changes (`git commit -am 'Add feature'`)
4. Push to the branch (`git push origin feature/your-feature`)
5. Create a Pull Request

## Running Tests

```bash
nbb run-tests.cljs
```

## Running Demo

```bash
nbb run-demo.cljs
```

## Code Style

- Use 2-space indentation
- Follow existing Clojure conventions
- Add docstrings to public functions
- Write tests for new functionality

## Scope

This actor handles **restaurant back-office administrative coordination only**:
- Table/reservation scheduling logistics
- Order-queue status tracking
- Non-food supply coordination
- Staff shift proposals
- Safety concern escalation

It does **NOT** handle:
- Food-safety/health-inspection determinations
- Recipe/menu-content decisions
- Food-handling-technique decisions
- Safety-authority overrides

Any proposals in the excluded scope will be rejected by the Governor.

## License

By contributing, you agree that your contributions will be licensed under the
AGPL-3.0 License.
