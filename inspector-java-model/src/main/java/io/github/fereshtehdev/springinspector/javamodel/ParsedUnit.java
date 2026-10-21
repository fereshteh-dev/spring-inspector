package io.github.fereshtehdev.springinspector.javamodel;

import com.github.javaparser.ast.CompilationUnit;
import io.github.fereshtehdev.springinspector.core.domain.change.SourceFile;
import io.github.fereshtehdev.springinspector.javamodel.patch.LineIndex;

record ParsedUnit(SourceFile source, CompilationUnit unit, LineIndex lines) {}
