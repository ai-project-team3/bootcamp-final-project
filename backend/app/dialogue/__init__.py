"""Dialogue repair (#323): take in a reply that is not an answer instead of asking the next slot.

decide  — a quick decider reads a short state and says what the child meant (an intent)
policy  — rules turn the intent into an act and the slots to take back (the LLM signals, the rules decide)
recipes — one function per act: what the line model is told, and which slot its question is about
"""
