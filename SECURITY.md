# Security Policy

## Threat Model

This actor is a **coordination-only** system for restaurant back-office logistics.
It has no authority over food-safety, health-inspection, recipe/menu decisions, or
food-handling technique. The Governor enforces this scope permanently via three HARD checks.

### Out of Scope (Never Auto-commits)
- Food-safety determinations
- Health-inspection compliance decisions
- Recipe/menu-content changes
- Food-handling technique decisions
- Safety-authority overrides

### In Scope (Auto-commits at appropriate phase)
- Table/reservation scheduling
- Order-queue status tracking
- Non-food supply coordination
- Staff shift proposals
- Safety concern escalation (always escalates to human review)

## Known Limitations

1. **No clinical judgment authority**: This actor cannot make food-safety or health decisions.
   All safety concerns flag for human review — no auto-commit, ever.

2. **No liability for deployment decisions**: Deploying this actor in a jurisdiction
   requires local food-service licensing/compliance review. This codebase is not
   a substitute for regulatory compliance infrastructure.

3. **Governor is deterministic, not LLM-based**: The demo Advisor and Governor use
   hardcoded rules, not learned models. Production would require additional quality
   assurance on any learned components.

## Reporting Security Issues

Please report security vulnerabilities to jun784@gmail.com with the subject
"Security Issue: cloud-itonami-isic-561".

Do not disclose vulnerabilities publicly until a fix is available.

## No Warranty

THIS SOFTWARE IS PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
